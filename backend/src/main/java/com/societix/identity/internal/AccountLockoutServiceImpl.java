package com.societix.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.societix.identity.api.AccountLockoutService;

/**
 * Default {@link AccountLockoutService} (task 10.2; design.md ADR-3; Req 2.7, 2.8, 2.9).
 *
 * <p>Implements the approved RD3/OQ3 policy over the {@code users} security counters:
 * <ul>
 *   <li>Failed attempts are counted within a rolling {@value AccountLockoutService#FAILURE_WINDOW_MINUTES}-minute
 *       window (tracked by {@code failure_window_started_at}); a window older than that resets and a
 *       fresh failure starts a new window (Req 2.7).</li>
 *   <li>When failures within the window exceed {@value AccountLockoutService#MAX_FAILED_ATTEMPTS},
 *       the account is locked for a duration that grows with each lockout cycle ? initial
 *       {@value AccountLockoutService#INITIAL_LOCKOUT_MINUTES} minutes, doubling per cycle, capped at
 *       {@value AccountLockoutService#MAX_LOCKOUT_MINUTES} minutes. The lock is always a finite
 *       {@code lockout_until} instant, so there is NEVER a permanent lockout (Req 2.8).</li>
 *   <li>A successful sign-in clears the counter, the window, and any lockout (but intentionally
 *       retains {@code lockout_count} so a serial abuser's backoff keeps growing ? see note).</li>
 * </ul>
 *
 * <p>All lookups are by (case-insensitive) email and are <em>enumeration-safe</em>: an unknown
 * account reports "not locked" and silently no-ops on record calls, so lockout state cannot be used
 * to probe for account existence (Req 2.9). Time is read from an injected {@link Clock} so the
 * windowing/backoff is deterministically unit-testable.
 */
@Service
public class AccountLockoutServiceImpl implements AccountLockoutService {

    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * @param userRepository the account store carrying the security counters
     * @param clock          the time source (injectable for tests); defaults to system UTC in prod
     */
    public AccountLockoutServiceImpl(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        return findUser(email)
                .map(user -> {
                    OffsetDateTime until = user.getLockoutUntil();
                    return until != null && now().isBefore(until);
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public void recordFailedAttempt(String email) {
        findUser(email).ifPresent(user -> {
            OffsetDateTime now = now();

            // Start a fresh window if there is none or the existing one has elapsed (Req 2.7).
            OffsetDateTime windowStart = user.getFailureWindowStartedAt();
            boolean windowExpired = windowStart == null
                    || !now.isBefore(windowStart.plus(Duration.ofMinutes(FAILURE_WINDOW_MINUTES)));
            if (windowExpired) {
                user.setFailureWindowStartedAt(now);
                user.setFailedAttempts(0);
            }

            user.setFailedAttempts(user.getFailedAttempts() + 1);

            if (user.getFailedAttempts() > MAX_FAILED_ATTEMPTS) {
                // Threshold exceeded: open (or escalate) a finite, progressively longer lockout.
                int nextCycle = user.getLockoutCount() + 1;
                user.setLockoutCount(nextCycle);
                user.setLockoutUntil(now.plus(lockoutDurationFor(nextCycle)));
                // Begin counting afresh after the lock so the next burst is measured cleanly.
                user.setFailedAttempts(0);
                user.setFailureWindowStartedAt(null);
            }
            userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public void recordSuccessfulAttempt(String email) {
        findUser(email).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setFailureWindowStartedAt(null);
            user.setLockoutUntil(null);
            // lockout_count is intentionally NOT reset: a successful login between abuse bursts must
            // not let an attacker cheaply reset the progressive backoff. It only grows; the lock is
            // still always finite (capped), so this never becomes a permanent lock (Req 2.8).
            userRepository.save(user);
        });
    }

    /**
     * Computes the lockout duration for the given 1-based cycle: initial minutes doubled per prior
     * cycle, capped at the maximum. Always finite (Req 2.8).
     */
    private static Duration lockoutDurationFor(int cycle) {
        long minutes = INITIAL_LOCKOUT_MINUTES;
        for (int i = 1; i < cycle && minutes < MAX_LOCKOUT_MINUTES; i++) {
            minutes = Math.min((long) MAX_LOCKOUT_MINUTES, minutes * 2);
        }
        return Duration.ofMinutes(Math.min(minutes, MAX_LOCKOUT_MINUTES));
    }

    private Optional<User> findUser(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
