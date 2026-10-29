package com.societix.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts/decrypts small secrets (the TOTP shared secret) for storage at rest using AES-GCM
 * (task 13.1; Req 24.6). The data key comes from the {@code SOCIETIX_MFA_ENCRYPTION_KEY}
 * environment variable (Base64, 32 bytes = AES-256) and is NEVER committed (Req 17.5); see
 * {@code .env.example}. Each ciphertext embeds a fresh random 96-bit IV so encrypting the same
 * secret twice yields different ciphertexts, and GCM provides authenticated encryption (tamper
 * detection on decrypt).
 */
@Component
public class SecretEncryptor {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretEncryptor(@Value("${societix.mfa.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            // Fail loud rather than silently using a weak/absent key. A real key must be provided
            // via SOCIETIX_MFA_ENCRYPTION_KEY (Req 17.5, 24.6).
            throw new IllegalStateException(
                    "societix.mfa.encryption-key (SOCIETIX_MFA_ENCRYPTION_KEY) must be set to a "
                            + "Base64-encoded 32-byte AES key");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException("MFA encryption key must decode to 16, 24, or 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Encrypts plaintext, returning Base64(iv || ciphertext+tag). */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /** Decrypts a value produced by {@link #encrypt(String)}. */
    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }
}
