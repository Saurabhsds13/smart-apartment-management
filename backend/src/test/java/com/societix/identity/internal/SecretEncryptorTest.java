package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SecretEncryptor} (task 13.1; Req 24.6). Verifies AES-GCM round-trip,
 * that the ciphertext is not the plaintext and differs per call (random IV), tamper detection, and
 * that a missing/invalid key fails loudly.
 */
@DisplayName("SecretEncryptor")
class SecretEncryptorTest {

    /** A valid Base64-encoded 32-byte AES-256 key for tests (not a real/production key). */
    private static final String TEST_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final SecretEncryptor encryptor = new SecretEncryptor(TEST_KEY);

    @Test
    @DisplayName("encrypt then decrypt returns the original plaintext")
    void roundTrip() {
        String secret = "JBSWY3DPEHPK3PXP";
        String encrypted = encryptor.encrypt(secret);
        assertThat(encryptor.decrypt(encrypted)).isEqualTo(secret);
    }

    @Test
    @DisplayName("ciphertext is not the plaintext and differs on each call (random IV)")
    void ciphertextHidesAndVaries() {
        String secret = "JBSWY3DPEHPK3PXP";
        String a = encryptor.encrypt(secret);
        String b = encryptor.encrypt(secret);
        assertThat(a).doesNotContain(secret);
        assertThat(a).isNotEqualTo(b);           // fresh IV each time
        assertThat(encryptor.decrypt(a)).isEqualTo(encryptor.decrypt(b)).isEqualTo(secret);
    }

    @Test
    @DisplayName("tampered ciphertext fails authenticated decryption")
    void tamperDetected() {
        String encrypted = encryptor.encrypt("JBSWY3DPEHPK3PXP");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01; // flip a bit in the tag/ciphertext
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a missing encryption key fails loudly at construction (Req 17.5)")
    void missingKeyFailsLoud() {
        assertThatThrownBy(() -> new SecretEncryptor("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretEncryptor(null)).isInstanceOf(IllegalStateException.class);
    }
}
