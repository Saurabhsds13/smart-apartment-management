package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TotpGenerator} (task 13.1; Req 24.4). Verifies RFC 4648 Base32 round-trip,
 * that a freshly generated secret verifies its own current code, skew tolerance, and that a wrong
 * code or a code far outside the window is rejected.
 */
@DisplayName("TotpGenerator")
class TotpGeneratorTest {

    @Test
    @DisplayName("Base32 encode/decode round-trips arbitrary bytes")
    void base32RoundTrip() {
        byte[] original = "societix-totp-secret".getBytes();
        String encoded = TotpGenerator.base32Encode(original);
        assertThat(TotpGenerator.base32Decode(encoded)).isEqualTo(original);
    }

    @Test
    @DisplayName("a generated secret verifies its own current code")
    void currentCodeVerifies() {
        String secret = TotpGenerator.generateSecret();
        Instant now = Instant.parse("2025-06-01T12:00:00Z");
        String code = currentCode(secret, now);
        assertThat(TotpGenerator.verify(secret, code, now)).isTrue();
    }

    @Test
    @DisplayName("a code from the previous/next 30s step still verifies (skew tolerance)")
    void skewToleranceWithinOneStep() {
        String secret = TotpGenerator.generateSecret();
        Instant now = Instant.parse("2025-06-01T12:00:00Z");
        String prevStepCode = currentCode(secret, now.minusSeconds(30));
        String nextStepCode = currentCode(secret, now.plusSeconds(30));
        assertThat(TotpGenerator.verify(secret, prevStepCode, now)).isTrue();
        assertThat(TotpGenerator.verify(secret, nextStepCode, now)).isTrue();
    }

    @Test
    @DisplayName("a code two steps away does NOT verify")
    void beyondSkewRejected() {
        String secret = TotpGenerator.generateSecret();
        Instant now = Instant.parse("2025-06-01T12:00:00Z");
        String farCode = currentCode(secret, now.plusSeconds(120));
        assertThat(TotpGenerator.verify(secret, farCode, now)).isFalse();
    }

    @Test
    @DisplayName("a wrong/malformed code is rejected")
    void wrongCodeRejected() {
        String secret = TotpGenerator.generateSecret();
        Instant now = Instant.parse("2025-06-01T12:00:00Z");
        assertThat(TotpGenerator.verify(secret, "000000", now.plusSeconds(1_000_000))).isFalse();
        assertThat(TotpGenerator.verify(secret, "abc", now)).isFalse();
        assertThat(TotpGenerator.verify(secret, null, now)).isFalse();
    }

    /** Deterministic exact-step code for the step containing {@code at} (no skew). */
    private static String currentCode(String secret, Instant at) {
        return TotpGenerator.codeAt(secret, at);
    }
}
