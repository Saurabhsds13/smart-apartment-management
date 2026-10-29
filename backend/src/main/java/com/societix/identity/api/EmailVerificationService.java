package com.societix.identity.api;

/**
 * Email-verification flow: issue a single-use verification credential and confirm control of an
 * email address with it (task 10.3; design.md "Identity & Auth"; Req 2.14).
 *
 * <p>Until a user confirms the verification credential, the platform does not treat the email
 * address as verified (Req 2.14). The credential is single-use and time-limited.
 */
public interface EmailVerificationService {

    /** Verification-credential lifetime in minutes. */
    int VERIFICATION_TOKEN_TTL_MINUTES = 60;

    /**
     * Issues a single-use email-verification credential for the account and emails the raw token to
     * the address. A no-op (enumeration-safe) for an unknown account.
     *
     * @param email the account email (case-insensitive)
     */
    void requestVerification(String email);

    /**
     * Confirms an email address using a raw verification token. On success the account's email is
     * marked verified and the token is consumed (single-use).
     *
     * @param rawToken the raw verification token from the email
     * @throws com.societix.common.api.ValidationException if the token is unknown, expired, or
     *     already used (the email remains unverified)
     */
    void confirmVerification(String rawToken);
}
