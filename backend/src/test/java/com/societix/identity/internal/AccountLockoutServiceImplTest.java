package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.identity.api.AccountLockoutService;

/**
 * Unit tests for {@link AccountLockoutServiceImpl} (task 10.2; Req 2.7, 2.8, 2.9).
 *
 * <p>Drives the real service against a mocked {@link UserRepository} and a controllable mutable
 * {@link Clock}, so the rolling window, threshold, progressive backoff, no-permanent-lock, and
 * enumeration-safety are all asserted deterministically without a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockoutServiceImpl")
class AccountLockoutServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;

    private MutableClock clock;
    private AccountLockoutService service;
    private User user;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        service = new AccountLockoutServiceImpl(userRepository, clock);
        user = new User(EMAIL, "Test User");
        lenient().when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("five failures within the window do not lock; the sixth does (Req 2.7)")
    void locksAfterThresholdExceeded() {
        for (int i = 0; i < AccountLockoutService.MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailedAttempt(EMAIL);
            assertThat(service.isLocked(EMAIL))
                    .as("must not lock on attempt %d (<= threshold)", i + 1)
                    .isFalse();
        }
        // The 6th failure crosses the threshold -> locked.
        service.recordFailedAttempt(EMAIL);
        assertThat(service.isLocked(EMAIL)).isTrue();
        assertThat(user.getLockoutUntil()).isNotNull();
    }

    @Test
    @DisplayName("failures older than the 15-minute window do not accumulate into a lock (Req 2.7)")
    void windowResetsAfterExpiry() {
        // Five failures, then advance past the window, then five more -> still not locked because
        // the old window reset (the second burst is only 5, not >5, in its own fresh window).
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt(EMAIL);
        }
        clock.advance(Duration.ofMinutes(AccountLockoutService.FAILURE_WINDOW_MINUTES + 1));
        for (int i = 0; i < 5; i++) {
            service.recordFailedAttempt(EMAIL);
        }
        assertThat(service.isLocked(EMAIL))
                .as("two separate sub-threshold bursts in distinct windows must not lock")
                .isFalse();
    }

    @Test
    @DisplayName("repeated lockout cycles get progressively longer but never permanent (Req 2.8)")
    void progressiveButBoundedBackoff() {
        Duration firstLock = triggerOneLockoutCycleAndReturnDuration();
        assertThat(firstLock).isEqualTo(Duration.ofMinutes(AccountLockoutService.INITIAL_LOCKOUT_MINUTES));

        // Let the first lock elapse, then trigger a second cycle: it must be strictly longer.
        clock.advance(firstLock.plusMinutes(1));
        Duration secondLock = triggerOneLockoutCycleAndReturnDuration();
        assertThat(secondLock).isGreaterThan(firstLock);

        // However long the escalation runs, the lock is always a finite instant (never permanent).
        clock.advance(secondLock.plusMinutes(1));
        for (int cycle = 0; cycle < 20; cycle++) {
            Duration d = triggerOneLockoutCycleAndReturnDuration();
            assertThat(d).isNotNull();
            assertThat(d).isLessThanOrEqualTo(Duration.ofMinutes(AccountLockoutService.MAX_LOCKOUT_MINUTES));
            assertThat(user.getLockoutUntil()).isNotNull(); // finite instant, not "forever"
            clock.advance(d.plusMinutes(1));
        }
    }

    @Test
    @DisplayName("a successful sign-in clears the failure counter and lockout (Req 2.9)")
    void successClearsCounters() {
        for (int i = 0; i <= AccountLockoutService.MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailedAttempt(EMAIL);
        }
        assertThat(service.isLocked(EMAIL)).isTrue();

        clock.advance(Duration.ofMinutes(AccountLockoutService.INITIAL_LOCKOUT_MINUTES + 1));
        service.recordSuccessfulAttempt(EMAIL);

        assertThat(service.isLocked(EMAIL)).isFalse();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockoutUntil()).isNull();
    }

    @Test
    @DisplayName("an unknown account is enumeration-safe: never locked, record calls no-op (Req 2.9)")
    void unknownAccountIsEnumerationSafe() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThat(service.isLocked("ghost@example.com")).isFalse();
        service.recordFailedAttempt("ghost@example.com");
        service.recordSuccessfulAttempt("ghost@example.com");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("null/blank email is treated as unknown and never touches the store")
    void nullOrBlankEmailIsSafe() {
        assertThat(service.isLocked(null)).isFalse();
        assertThat(service.isLocked("  ")).isFalse();
        service.recordFailedAttempt(null);
        verify(userRepository, never()).save(any(User.class));
    }

    /** Drives six failures in one window to open a single lockout cycle and returns its duration. */
    private Duration triggerOneLockoutCycleAndReturnDuration() {
        OffsetDateTime before = OffsetDateTime.now(clock);
        for (int i = 0; i <= AccountLockoutService.MAX_FAILED_ATTEMPTS; i++) {
            service.recordFailedAttempt(EMAIL);
        }
        return Duration.between(before, user.getLockoutUntil());
    }

    /** A simple advanceable UTC clock for deterministic windowing/backoff assertions. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            this.instant = this.instant.plus(by);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
