package com.societix.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates cryptographically-strong single-use tokens and the one-way hash stored for them
 * (task 10.3; Req 2.5, 2.6, 2.14).
 *
 * <p>A raw token is 32 bytes of {@link SecureRandom} entropy, URL-safe Base64 encoded (so it is
 * safe to embed in an email link). Only {@link #hash(String) SHA-256(raw)} is persisted, so the
 * stored value cannot be reversed into a usable credential; a presented raw token is hashed the
 * same way and matched against the stored hash. SHA-256 is appropriate here (not an adaptive hash)
 * because the token is high-entropy random, not a low-entropy human secret.
 */
final class SingleUseTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SingleUseTokens() {
    }

    /** Generates a new URL-safe raw token (to email to the user; never stored). */
    static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Computes the stored one-way hash (SHA-256 hex) of a raw token. */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
