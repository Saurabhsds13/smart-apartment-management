package com.societix.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers the platform's adaptive {@link PasswordEncoder} bean (task 9.2; design.md
 * "Identity &amp; Auth"; Req 2.4).
 *
 * <p><strong>Algorithm choice.</strong> The encoder is {@link Sha256BCryptPasswordEncoder}: BCrypt
 * is the pragmatic adaptive core — bundled with {@code spring-security-crypto} (already on the
 * classpath via the security starter), it salts every hash with a fresh random salt embedded in the
 * output, and its work factor (cost) is tunable so hashing stays deliberately slow as hardware
 * improves (the "adaptive" property required by Req 2.4). Because BCrypt only consumes 72 bytes of
 * input, the password is first reduced with SHA-256 so the full 12–128 character policy (Req 2.3)
 * is honoured without truncating entropy. The cost is pinned to {@value #BCRYPT_STRENGTH}
 * (BCrypt's default), a balanced value for interactive logins; it can be raised later without
 * invalidating existing hashes because the cost is encoded in each stored hash.
 *
 * <p>This bean lives in {@code identity.internal} because the encoder is an implementation detail;
 * other modules depend on {@link com.societix.identity.api.PasswordService}, not on the encoder.
 */
@Configuration
public class PasswordEncoderConfig {

    /** BCrypt work factor (log2 of rounds). 10 is the library default — a balanced cost. */
    static final int BCRYPT_STRENGTH = 10;

    /**
     * Supplies the application-wide adaptive password encoder.
     *
     * @return a salted, adaptive {@link Sha256BCryptPasswordEncoder} (SHA-256 pre-hash + BCrypt)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Sha256BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
