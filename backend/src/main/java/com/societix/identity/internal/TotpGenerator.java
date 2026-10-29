package com.societix.identity.internal;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 Time-based One-Time Password (TOTP) generator/verifier (task 13.1; Req 24.4).
 *
 * <p>Implements the standard directly on the JDK's {@code javax.crypto} (HMAC-SHA1 over a
 * 30-second time step, 6-digit codes) rather than pulling in a third-party library, keeping the
 * dependency set minimal. Codes are compatible with standard authenticator apps (Google
 * Authenticator, Authy, etc.). Verification accepts the current step plus one step either side to
 * tolerate clock skew. Secrets are handled as Base32 (the format authenticator apps consume in an
 * {@code otpauth://} URI).
 */
final class TotpGenerator {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SKEW_STEPS = 1;
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int SECRET_BYTES = 20; // 160-bit secret (RFC 6238 SHA-1 recommendation)
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpGenerator() {
    }

    /** Generates a new random Base32-encoded TOTP secret suitable for an authenticator app. */
    static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Verifies a presented 6-digit code against the Base32 secret at the given instant (+/- skew). */
    static boolean verify(String base32Secret, String code, Instant at) {
        if (base32Secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        long step = at.getEpochSecond() / TIME_STEP_SECONDS;
        byte[] key = base32Decode(base32Secret);
        for (long s = step - SKEW_STEPS; s <= step + SKEW_STEPS; s++) {
            if (constantTimeEquals(generateCode(key, s), code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the exact code for the time step containing {@code at}, with NO skew tolerance.
     * Package-private, for deterministic test code generation (production verification uses the
     * skew-tolerant {@link #verify}).
     */
    static String codeAt(String base32Secret, Instant at) {
        long step = at.getEpochSecond() / TIME_STEP_SECONDS;
        return generateCode(base32Decode(base32Secret), step);
    }

    /** Generates the code for a given secret + time step (RFC 6238 / RFC 4226 truncation). */
    private static String generateCode(byte[] key, long step) {
        byte[] data = ByteBuffer.allocate(8).putLong(step).array();
        byte[] hash = hmacSha1(key, data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    // --- Base32 (RFC 4648, no padding) ---------------------------------------

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.trim().replace("=", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int val = indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < BASE32.length; i++) {
            if (BASE32[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
