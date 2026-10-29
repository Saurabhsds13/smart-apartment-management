package com.societix.identity.api;

/**
 * Public contract for the account login-throttling and lockout policy (task 10.2; design.md ADR-3;
 * Req 2.2, 2.7, 2.8, 2.9). The authentication flow consults this service around every sign-in
 * attempt so that repeated failures temporarily block an account without ever locking it
 * permanently (Req 2.8), and so that the response never reveals whether the credentials were valid
 * for a currently-blocked account (Req 2.9).
 *
 * <h2>Approved policy (RD3 / OQ3)</h2>
 * <ul>
 *   <li>More than {@value #MAX_FAILED_ATTEMPTS} failed attempts within a
 *       {@value #FAILURE_WINDOW_MINUTES}-minute window triggers a lockout (Req 2.7).</li>
 *   <li>The initial lockout lasts {@value #INITIAL_LOCKOUT_MINUTES} minutes; repeated lockout
 *       cycles apply progressively longer delays, capped at {@value #MAX_LOCKOUT_MINUTES} minutes,
 *       and NEVER a permanent lock (Req 2.8).</li>
 *   <li>A successful sign-in clears the failed-attempt counter and any lockout.</li>
 * </ul>
 *
 * <p>The service operates purely on the account's security counters; it performs no credential
 * verification itself and returns no information that distinguishes "locked" from "bad password" to
 * callers that must stay enumeration-safe (Req 2.9 is satisfied by the caller returning a single
 * generic error for both).
 */
public interface AccountLockoutService {

    /** Failed attempts within the window beyond which the account is locked (Req 2.7). */
    int MAX_FAILED_ATTEMPTS = 5;

    /** Rolling window, in minutes, over which failed attempts are counted (Req 2.7). */
    int FAILURE_WINDOW_MINUTES = 15;

    /** Initial temporary lockout duration, in minutes (Req 2.7). */
    int INITIAL_LOCKOUT_MINUTES = 15;

    /** Upper bound, in minutes, on the progressively-growing lockout; never permanent (Req 2.8). */
    int MAX_LOCKOUT_MINUTES = 24 * 60;

    /**
     * Reports whether the account identified by {@code email} is currently within an active lockout
     * window and must therefore be refused a sign-in attempt (Req 2.9). Unknown accounts report
     * {@code false} so that lockout state cannot be used to probe for account existence.
     *
     * @param email the login identifier (case-insensitive)
     * @return {@code true} if a sign-in attempt must be blocked right now
     */
    boolean isLocked(String email);

    /**
     * Records a failed sign-in for the account and applies the lockout policy. Counts the failure
     * within the rolling window and, once the threshold is exceeded, sets or extends the temporary
     * lockout with a progressively longer (but bounded, never permanent) duration (Req 2.7, 2.8).
     * A no-op for an unknown account (enumeration-safe).
     *
     * @param email the login identifier (case-insensitive)
     */
    void recordFailedAttempt(String email);

    /**
     * Clears the failed-attempt counter and any active lockout for the account after a successful
     * sign-in. A no-op for an unknown account.
     *
     * @param email the login identifier (case-insensitive)
     */
    void recordSuccessfulAttempt(String email);
}
