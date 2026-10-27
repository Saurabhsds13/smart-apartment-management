package com.societix.identity.internal;

import com.societix.common.api.ValidationException;
import com.societix.identity.api.PasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Default {@link PasswordService} implementation (task 9.2; Req 2.3, 2.4).
 *
 * <p>It enforces the 12–128 character policy (Req 2.3) and delegates the actual hashing/verification
 * to the injected adaptive {@link PasswordEncoder} (a BCrypt encoder registered by
 * {@link PasswordEncoderConfig}), which salts every hash with a fresh random salt and is tunably
 * slow (Req 2.4). The raw password is never stored, logged, or placed in an error message.
 */
@Service
public class PasswordServiceImpl implements PasswordService {

    /** Field name reported in validation errors; never accompanied by the rejected value (Req 17.10). */
    private static final String FIELD = "password";

    private final PasswordEncoder passwordEncoder;

    /**
     * Creates the service with the application's adaptive encoder.
     *
     * @param passwordEncoder the salted adaptive one-way encoder (BCrypt; see
     *                        {@link PasswordEncoderConfig})
     */
    public PasswordServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Rejects a {@code null} password and any password whose length falls outside the
     * {@value #MIN_LENGTH}–{@value #MAX_LENGTH} range before hashing. The error names the
     * {@code password} field and the allowed range only — never the rejected value.
     */
    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new ValidationException(FIELD, "required", "A password is required.");
        }
        int length = rawPassword.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new ValidationException(FIELD, "length-out-of-range",
                    "Password must be between %d and %d characters.".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code false} rather than throwing when either argument is {@code null} or blank,
     * so callers in the authentication flow (task 10.x) can treat a missing credential as a simple
     * non-match without leaking why.
     */
    @Override
    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }
}
