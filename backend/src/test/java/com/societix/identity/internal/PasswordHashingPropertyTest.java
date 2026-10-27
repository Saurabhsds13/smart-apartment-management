package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.societix.identity.api.PasswordService;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for <strong>password hashing</strong> (task 9.3; design.md Correctness
 * Property 11; Req 2.4).
 *
 * <p><strong>Feature: societix-platform, Property 11: Password hashing (salted adaptive one-way,
 * never plaintext, unique salt)</strong> — for all valid passwords (length within the 12–128
 * character policy), the stored credential produced by {@link PasswordService#hash(String)} is a
 * salted, adaptive, one-way hash that:
 * <ul>
 *   <li><strong>is never the plaintext</strong> — the encoded hash is neither equal to nor contains
 *       the raw password (one-way, never plaintext);</li>
 *   <li><strong>carries a unique random salt</strong> — hashing the same password twice yields two
 *       <em>different</em> encoded strings, yet both still verify against the original password
 *       (salted, but correct);</li>
 *   <li><strong>verifies correctly</strong> — {@code verify(correctPassword, hash)} is {@code true}
 *       and {@code verify(aDifferentPassword, hash)} is {@code false};</li>
 *   <li><strong>is adaptive</strong> — the encoded hash is in BCrypt's modular-crypt format
 *       ({@code $2a$}/{@code $2b$}/{@code $2y$}) and embeds an explicit two-digit cost, the tunable
 *       work factor that makes the algorithm adaptive.</li>
 * </ul>
 *
 * <h2>Why this is a faithful, Docker-free property</h2>
 * The guarantee lives entirely in the {@link PasswordServiceImpl} policy gate plus the real
 * {@link Sha256BCryptPasswordEncoder} (SHA-256 pre-hash then BCrypt) — pure, in-process logic with
 * no database or Spring context. The test drives the production service directly against a real
 * encoder, so it runs fast and locally. A <strong>low BCrypt cost (strength 4)</strong> keeps each
 * of the 100+ tries quick while still exercising genuine salted, adaptive BCrypt. The generators
 * cover the full 12–128 character policy and deliberately include varied Unicode and maximal-length
 * inputs, so the &gt;72-byte cases that exercise the SHA-256 pre-hash path (BCrypt's 72-byte input
 * limit) are routinely generated.
 */
@org.junit.jupiter.api.Tag("property")
@net.jqwik.api.Tag("property")
class PasswordHashingPropertyTest {

    /** A real adaptive encoder at a low cost keeps 100+ tries fast while exercising real BCrypt. */
    private final PasswordService service = new PasswordServiceImpl(new Sha256BCryptPasswordEncoder(4));

    /** Matches BCrypt's modular-crypt format: version marker + two-digit cost + 22-char salt + 31-char hash. */
    private static final java.util.regex.Pattern BCRYPT_FORMAT =
            java.util.regex.Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    /**
     * For all valid passwords, the stored hash is a salted adaptive one-way credential that is never
     * the plaintext, verifies the original password, rejects a different one, uses a fresh salt on
     * every store (two hashes differ yet both verify), and is in BCrypt's adaptive (cost-bearing)
     * format.
     *
     * <p><strong>Validates: Requirements 2.4</strong>
     */
    @Property(tries = 200)
    void validPasswordsAreHashedSaltedAdaptiveOneWayAndNeverPlaintext(
            @ForAll("validPasswords") String password) {
        String hash = service.hash(password);

        // One-way, never plaintext: the encoded hash neither equals nor contains the raw password.
        assertThat(hash)
                .as("hash must never equal the plaintext")
                .isNotEqualTo(password);
        assertThat(hash)
                .as("hash must never contain the plaintext")
                .doesNotContain(password);

        // Adaptive: BCrypt modular-crypt format with an explicit two-digit cost (the work factor).
        assertThat(hash)
                .as("hash must be in adaptive BCrypt format with a cost marker")
                .matches(BCRYPT_FORMAT);
        assertThat(costOf(hash))
                .as("adaptive work factor must be present and positive")
                .isGreaterThan(0);

        // Correct: the original password verifies against its own hash.
        assertThat(service.verify(password, hash))
                .as("the correct password must verify against its hash")
                .isTrue();

        // Unique random salt: a second independent store of the SAME password yields a DIFFERENT
        // encoded hash, yet BOTH still verify — proving the difference is a fresh salt, not corruption.
        String secondHash = service.hash(password);
        assertThat(secondHash)
                .as("two independent stores of the same password must differ (unique salt)")
                .isNotEqualTo(hash);
        assertThat(service.verify(password, hash))
                .as("the first hash must still verify")
                .isTrue();
        assertThat(service.verify(password, secondHash))
                .as("the second hash must also verify")
                .isTrue();

        // A different password must NOT verify against the hash.
        String different = password + "X";
        assertThat(service.verify(different, hash))
                .as("a different password must not verify")
                .isFalse();
    }

    /** Extracts the two-digit BCrypt cost (work factor) from a modular-crypt hash, e.g. {@code $2a$04$...} -> 4. */
    private static int costOf(String bcryptHash) {
        // Layout: $<version>$<cost>$<salt+hash>; the cost is the second $-delimited field.
        String[] parts = bcryptHash.split("\\$");
        return Integer.parseInt(parts[2]);
    }

    /**
     * Generates valid passwords spanning the full 12–128 character policy. The policy measures
     * {@link String#length()} (UTF-16 code units), so this builds each password up to a randomly
     * chosen <em>code-unit</em> budget in {@code [MIN_LENGTH, MAX_LENGTH]}, consuming whole code
     * points and stopping before any that would overshoot the budget. Characters come from a wide
     * repertoire — ASCII plus multi-byte Unicode (accented letters, a CJK block, and supplementary
     * emoji) — so many generated passwords exceed BCrypt's 72-<em>byte</em> input limit well before
     * 128 characters, routinely exercising the SHA-256 pre-hash path the construction relies on for
     * long passwords. Every produced value therefore has {@code length()} within the policy range by
     * construction: valid inputs only, as the property requires.
     */
    @Provide
    Arbitrary<String> validPasswords() {
        Arbitrary<Integer> budgets = Arbitraries.integers()
                .between(PasswordService.MIN_LENGTH, PasswordService.MAX_LENGTH);
        return budgets.flatMap(budget ->
                // Oversupply code points, then pack as many as fit the UTF-16 budget exactly.
                passwordChars().list().ofSize(budget).map(chars -> packToBudget(chars, budget)));
    }

    /**
     * Packs code-point strings into a password whose {@link String#length()} is as close as possible
     * to {@code budget} without exceeding it, then pads with single-code-unit ASCII so the result
     * lands exactly on the budget — guaranteeing a valid password even when the last multi-code-unit
     * character would have overshot.
     */
    private static String packToBudget(java.util.List<String> chars, int budget) {
        StringBuilder sb = new StringBuilder(budget);
        for (String ch : chars) {
            if (sb.length() + ch.length() > budget) {
                continue;
            }
            sb.append(ch);
        }
        while (sb.length() < budget) {
            sb.append('x');
        }
        return sb.toString();
    }

    /**
     * A character repertoire mixing ASCII printable characters with multi-byte Unicode so generated
     * passwords exercise both the common case and the &gt;72-byte BCrypt pre-hash path. Each element
     * is a {@link String} (not a {@code char}) because emoji are supplementary-plane code points that
     * need a surrogate pair, which a single {@code char} cannot hold.
     */
    private Arbitrary<String> passwordChars() {
        Arbitrary<String> ascii = Arbitraries.chars()
                .range(' ', '~')
                .map(String::valueOf);
        Arbitrary<String> accented = Arbitraries.of(
                "á", "é", "í", "ó", "ú", "ñ", "ü", "ç", "ß", "ø", "å");
        Arbitrary<String> cjk = Arbitraries.chars()
                .range('\u4E00', '\u9FFF')
                .map(String::valueOf);
        Arbitrary<String> emoji = Arbitraries.of("😀", "🔐", "🚀", "🎉", "🧪", "✅");
        // Weight ASCII most heavily (realistic), with meaningful multi-byte representation.
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(6, ascii),
                net.jqwik.api.Tuple.of(2, accented),
                net.jqwik.api.Tuple.of(1, cjk),
                net.jqwik.api.Tuple.of(1, emoji));
    }
}
