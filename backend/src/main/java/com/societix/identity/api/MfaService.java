package com.societix.identity.api;

import java.util.UUID;

/**
 * TOTP multi-factor authentication: enrollment, confirmation, verification, and step-up
 * (task 13.1, 13.2; design.md "MFA"; Req 24.4, 24.5, 24.6, 24.7, 24.8, 2.13).
 *
 * <p>Enrollment issues a secret + recovery codes once ({@link #beginEnrollment}); the user confirms
 * a first valid code to enable the factor ({@link #confirmEnrollment}). Thereafter
 * {@link #verify(UUID, String)} checks a presented TOTP code or a single-use recovery code ? used
 * both at sign-in (for roles where MFA is enforced, {@link MfaPolicy}) and for step-up
 * reauthentication before a sensitive operation (Req 2.13). Secrets are encrypted at rest and
 * recovery codes are hashed and single-use; neither is ever exposed after enrollment (Req 24.6).
 */
public interface MfaService {

    /**
     * Begins TOTP enrollment for the user: generates and stores (encrypted) a new secret and a set
     * of single-use recovery codes (stored hashed), and returns the raw secret, provisioning URI,
     * and recovery codes to show the user once. The factor is not yet enabled until
     * {@link #confirmEnrollment} succeeds.
     *
     * @param userId the enrolling user's id
     * @param accountLabel a label for the authenticator entry (e.g. the user's email)
     * @return the one-time enrollment material
     */
    MfaEnrollment beginEnrollment(UUID userId, String accountLabel);

    /**
     * Confirms enrollment by verifying a first TOTP code against the pending secret, enabling the
     * factor on success.
     *
     * @param userId the enrolling user's id
     * @param totpCode the 6-digit code from the authenticator app
     * @return {@code true} if the code was valid and MFA is now enabled
     */
    boolean confirmEnrollment(UUID userId, String totpCode);

    /**
     * Verifies a presented second factor for an enrolled, enabled user: either a current TOTP code
     * or a single-use recovery code (which is consumed on use). Used at sign-in and for step-up
     * reauthentication (Req 2.13).
     *
     * @param userId the user's id
     * @param codeOrRecovery a TOTP code or a recovery code
     * @return {@code true} if the factor verified
     */
    boolean verify(UUID userId, String codeOrRecovery);

    /**
     * Whether the user has an enabled MFA enrollment.
     *
     * @param userId the user's id
     * @return {@code true} if MFA is enrolled and enabled
     */
    boolean isEnrolled(UUID userId);
}
