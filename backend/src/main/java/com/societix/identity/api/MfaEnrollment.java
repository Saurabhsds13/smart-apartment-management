package com.societix.identity.api;

import java.util.List;

/**
 * The one-time result of beginning TOTP MFA enrollment (task 13.1; Req 24.4, 24.5). Returned to the
 * enrolling user exactly once, at enrollment time: the Base32 {@code secret} and the
 * {@code otpauthUri} let them add the account to an authenticator app, and the {@code recoveryCodes}
 * are shown once for safe-keeping. These raw values are NEVER persisted in plaintext ? the secret is
 * stored encrypted and the recovery codes are stored only as hashes (Req 24.6) ? so they cannot be
 * retrieved again after enrollment.
 *
 * @param secret        the Base32 TOTP secret (shown once)
 * @param otpauthUri    the {@code otpauth://totp/...} provisioning URI for QR display (shown once)
 * @param recoveryCodes the single-use recovery codes (shown once)
 */
public record MfaEnrollment(String secret, String otpauthUri, List<String> recoveryCodes) {

    public MfaEnrollment {
        recoveryCodes = recoveryCodes == null ? List.of() : List.copyOf(recoveryCodes);
    }
}
