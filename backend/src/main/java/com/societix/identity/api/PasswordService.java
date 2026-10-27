package com.societix.identity.api;

import com.societix.common.api.ValidationException;

/**
 * Public contract for password policy enforcement, hashing, and verification within the
 * <strong>identity</strong> module (task 9.2; design.md "Identity &amp; Auth"; Req 2.3, 2.4).
 *
 * <p>This service is the single place a raw password is accepted. It never persists, logs, or
 * returns the plaintext: {@link #hash(String)} validates the policy and returns an opaque encoded
 * hash suitable for storing in {@code users.password_hash}, and {@link #verify(String, String)}
 * compares a candidate against a stored hash. The complementary login, lockout, session, and
 * password-reset flows are out of scope here and owned by tasks 10.1–10.3.
 *
 * <h2>Password policy (Req 2.3)</h2>
 * A raw password is valid only when its length is between {@value #MIN_LENGTH} and
 * {@value #MAX_LENGTH} characters inclusive. {@link #hash(String)} rejects anything outside that
 * range with a {@link ValidationException} whose field error identifies the {@code password} field
 * <em>without</em> echoing the rejected value.
 *
 * <h2>Hashing guarantees (Req 2.4)</h2>
 * The stored credential is a salted, adaptive, one-way hash produced by an industry-standard
 * algorithm. Each call to {@link #hash(String)} uses a fresh random salt, so hashing the same
 * password twice yields two different encoded strings that both still verify.
 */
public interface PasswordService {

    /** Minimum permitted raw-password length in characters, inclusive (Req 2.3). */
    int MIN_LENGTH = 12;

    /** Maximum permitted raw-password length in characters, inclusive (Req 2.3). */
    int MAX_LENGTH = 128;

    /**
     * Validates the raw password against the {@value #MIN_LENGTH}–{@value #MAX_LENGTH} character
     * policy (Req 2.3) and, if it passes, returns a salted adaptive one-way hash to store in
     * {@code users.password_hash} (Req 2.4). The returned value is never the plaintext, and two
     * calls with the same input return different hashes because each uses a fresh random salt.
     *
     * @param rawPassword the plaintext password to hash; never stored or logged by this method
     * @return the encoded, salted, adaptive one-way hash
     * @throws ValidationException if {@code rawPassword} is {@code null} or its length is outside
     *                             the {@value #MIN_LENGTH}–{@value #MAX_LENGTH} range; the error
     *                             does not echo the rejected value (Req 17.10)
     */
    String hash(String rawPassword);

    /**
     * Verifies a candidate raw password against a previously stored hash (Req 2.4). This performs
     * no policy validation — it only reports whether the candidate produced the stored hash.
     *
     * @param rawPassword the candidate plaintext password
     * @param storedHash  the encoded hash previously returned by {@link #hash(String)}
     * @return {@code true} if the candidate matches the stored hash; {@code false} otherwise,
     *         including when either argument is {@code null} or blank
     */
    boolean verify(String rawPassword, String storedHash);
}
