package com.societix.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * An adaptive {@link PasswordEncoder} that pre-hashes the raw password with SHA-256 (Base64-encoded)
 * before handing it to {@link BCryptPasswordEncoder} (task 9.2; Req 2.3, 2.4).
 *
 * <p><strong>Why the pre-hash.</strong> BCrypt only consumes the first 72 bytes of its input and
 * rejects (or on some implementations silently truncates) anything longer. On its own it therefore
 * cannot honour the platform's 12–128 <em>character</em> password policy (Req 2.3): a 128-character
 * password — or any multi-byte password exceeding 72 bytes — would fail or lose entropy, so two
 * long passwords sharing a 72-byte prefix could verify against the same hash. Running the raw
 * password through SHA-256 first yields a fixed 32-byte digest (44 Base64 characters, well under
 * BCrypt's limit) that still depends on <em>every</em> character of the input. BCrypt then supplies
 * the salt, adaptiveness, and one-wayness (Req 2.4). This SHA-256-then-BCrypt construction is a
 * well-established pattern for supporting long passwords with BCrypt.
 *
 * <p>Base64 encoding the digest keeps the BCrypt input free of NUL bytes (which BCrypt treats as a
 * string terminator), so no entropy is lost there either.
 */
public class Sha256BCryptPasswordEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt;

    /**
     * Creates the encoder with the given BCrypt work factor.
     *
     * @param strength the BCrypt cost (log2 rounds); higher is slower and more resistant
     */
    public Sha256BCryptPasswordEncoder(int strength) {
        this.bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(prehash(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return bcrypt.matches(prehash(rawPassword), encodedPassword);
    }

    /**
     * Reduces an arbitrary-length password to a fixed-length, BCrypt-safe Base64 string that still
     * depends on every character of the input.
     *
     * @param rawPassword the raw password (never {@code null} when called from {@link #encode})
     * @return the Base64-encoded SHA-256 digest of the UTF-8 bytes of {@code rawPassword}
     */
    private static String prehash(CharSequence rawPassword) {
        byte[] digest = sha256().digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated on every conformant JRE; its absence is a broken runtime.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
