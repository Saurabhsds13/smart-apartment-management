package com.societix.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.societix.common.api.ApiFieldError;
import com.societix.common.api.ValidationException;
import com.societix.identity.api.PasswordService;
import com.societix.identity.internal.PasswordServiceImpl;
import com.societix.identity.internal.Sha256BCryptPasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Focused, example-based unit tests for {@link PasswordServiceImpl} (task 9.2; Req 2.3, 2.4).
 *
 * <p>These are deliberately isolated: the service is driven directly with a real
 * {@link Sha256BCryptPasswordEncoder} (the same adaptive encoder {@code PasswordEncoderConfig}
 * registers), with no Spring context, no database, and no Docker. They assert the policy boundaries, that the
 * stored hash is salted (two stores of the same password differ), that verification accepts the
 * correct password and rejects a wrong one, and that the hash is never the plaintext.
 *
 * <p>The universal "for all valid passwords" guarantees are covered by the property-based test in
 * task 9.3 (Property 11); this suite pins the concrete examples and edge cases.
 */
@DisplayName("PasswordServiceImpl")
class PasswordServiceImplTest {

    /** A real adaptive encoder at a low cost keeps the suite fast while exercising real BCrypt. */
    private final PasswordService service = new PasswordServiceImpl(new Sha256BCryptPasswordEncoder(4));

    /** Builds a raw password of exactly {@code length} printable characters. */
    private static String passwordOfLength(int length) {
        return "a".repeat(length);
    }

    @Nested
    @DisplayName("policy boundaries (Req 2.3)")
    class PolicyBoundaries {

        @Test
        @DisplayName("rejects a password one character below the minimum (11)")
        void rejectsElevenCharacters() {
            assertThatThrownBy(() -> service.hash(passwordOfLength(11)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("accepts a password exactly at the minimum (12)")
        void acceptsTwelveCharacters() {
            String hash = service.hash(passwordOfLength(12));
            assertThat(hash).isNotBlank();
        }

        @Test
        @DisplayName("accepts a password exactly at the maximum (128)")
        void acceptsOneHundredTwentyEightCharacters() {
            String hash = service.hash(passwordOfLength(128));
            assertThat(hash).isNotBlank();
        }

        @Test
        @DisplayName("rejects a password one character above the maximum (129)")
        void rejectsOneHundredTwentyNineCharacters() {
            assertThatThrownBy(() -> service.hash(passwordOfLength(129)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects a null password")
        void rejectsNull() {
            assertThatThrownBy(() -> service.hash(null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("the validation error names the password field and never echoes the value")
        void errorNamesPasswordFieldWithoutLeakingValue() {
            String rejected = passwordOfLength(11);

            ValidationException ex = (ValidationException)
                    org.assertj.core.api.Assertions.catchThrowable(() -> service.hash(rejected));

            assertThat(ex).isNotNull();
            assertThat(ex.getFieldErrors()).hasSize(1);
            ApiFieldError error = ex.getFieldErrors().get(0);
            assertThat(error.field()).isEqualTo("password");
            assertThat(error.message()).doesNotContain(rejected);
            assertThat(ex.getMessage()).doesNotContain(rejected);
        }
    }

    @Nested
    @DisplayName("hashing (Req 2.4)")
    class Hashing {

        @Test
        @DisplayName("the stored hash is never the plaintext")
        void hashIsNotPlaintext() {
            String raw = "correct horse battery";
            String hash = service.hash(raw);

            assertThat(hash).isNotEqualTo(raw);
            assertThat(hash).doesNotContain(raw);
        }

        @Test
        @DisplayName("the same password hashes to different values (random salt)")
        void samePasswordHashesToDifferentValues() {
            String raw = "correct horse battery";

            String first = service.hash(raw);
            String second = service.hash(raw);

            assertThat(first).isNotEqualTo(second);
            // ...yet both still verify, proving the difference is a salt, not corruption.
            assertThat(service.verify(raw, first)).isTrue();
            assertThat(service.verify(raw, second)).isTrue();
        }
    }

    @Nested
    @DisplayName("verification (Req 2.4)")
    class Verification {

        @Test
        @DisplayName("verify() accepts the correct password")
        void acceptsCorrectPassword() {
            String raw = "correct horse battery";
            String hash = service.hash(raw);

            assertThat(service.verify(raw, hash)).isTrue();
        }

        @Test
        @DisplayName("verify() rejects a wrong password")
        void rejectsWrongPassword() {
            String hash = service.hash("correct horse battery");

            assertThat(service.verify("wrong horse battery!!", hash)).isFalse();
        }

        @Test
        @DisplayName("verify() returns false for null or blank arguments rather than throwing")
        void returnsFalseForNullOrBlank() {
            String hash = service.hash("correct horse battery");

            assertThat(service.verify(null, hash)).isFalse();
            assertThat(service.verify("correct horse battery", null)).isFalse();
            assertThat(service.verify("correct horse battery", "   ")).isFalse();
        }
    }
}
