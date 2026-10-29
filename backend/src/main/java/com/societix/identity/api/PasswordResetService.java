package com.societix.identity.api;

/**
 * Password-reset flow: issue a single-use, time-limited reset credential and complete a reset with
 * it (task 10.3; design.md "Identity & Auth"; Req 2.5, 2.6).
 *
 * <p>The issued credential expires {@value #RESET_TOKEN_TTL_MINUTES} minutes after issuance and is
 * single-use; an expired or already-used credential is rejected and the current password is left
 * unchanged (Req 2.6). Requesting a reset is enumeration-safe: it behaves identically whether or
 * not the email maps to an account, so the caller cannot use it to probe for account existence.
 */
public interface PasswordResetService {

    /** Reset-credential lifetime in minutes (Req 2.5). */
    int RESET_TOKEN_TTL_MINUTES = 60;

    /**
     * Requests a password reset for the given email. If an account exists, issues a single-use
     * reset credential expiring in {@value #RESET_TOKEN_TTL_MINUTES} minutes and emails the raw
     * token to the address. Always returns normally (no indication of whether the account exists).
     *
     * @param email the account email (case-insensitive)
     */
    void requestReset(String email);

    /**
     * Completes a password reset using a raw reset token and a new password. The new password is
     * subject to the standard policy ({@link PasswordService}). On success the password is updated
     * and the token is consumed (single-use).
     *
     * @param rawToken    the raw reset token from the email
     * @param newPassword the new password to set (validated by policy)
     * @throws com.societix.common.api.ValidationException if the token is unknown, expired, or
     *     already used (current password left unchanged, Req 2.6), or the new password is invalid
     */
    void completeReset(String rawToken, String newPassword);
}
