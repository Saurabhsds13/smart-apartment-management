package com.societix.identity.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.societix.common.api.EmailMessage;
import com.societix.common.api.EmailSender;
import com.societix.common.api.ValidationException;
import com.societix.identity.api.PasswordResetService;
import com.societix.identity.api.PasswordService;

/**
 * Default {@link PasswordResetService} (task 10.3; Req 2.5, 2.6).
 *
 * <p>{@link #requestReset(String)} is enumeration-safe: it looks the account up and, only if it
 * exists, stores a single-use reset token (hash only) expiring in
 * {@value PasswordResetService#RESET_TOKEN_TTL_MINUTES} minutes and emails the raw token; either
 * way it returns normally. {@link #completeReset(String, String)} validates the presented token
 * (must exist, be unexpired, and be unconsumed), applies the standard password policy to the new
 * password, updates the hash, and consumes the token. An invalid/expired/used token is rejected
 * with a generic validation error and the current password is left unchanged (Req 2.6).
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final AccountTokenRepository tokenRepository;
    private final PasswordService passwordService;
    private final EmailSender emailSender;
    private final Clock clock;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            AccountTokenRepository tokenRepository,
            PasswordService passwordService,
            EmailSender emailSender,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordService = passwordService;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return; // enumeration-safe no-op
        }
        Optional<User> account = userRepository.findByEmailIgnoreCase(email);
        if (account.isEmpty()) {
            return; // do not reveal that the account does not exist
        }
        User user = account.get();
        String rawToken = SingleUseTokens.generateRawToken();
        OffsetDateTime expiresAt = now().plus(Duration.ofMinutes(RESET_TOKEN_TTL_MINUTES));
        tokenRepository.save(new AccountToken(
                user.getId(), AccountTokenType.password_reset, SingleUseTokens.hash(rawToken), expiresAt));
        emailSender.send(new EmailMessage(
                user.getEmail(),
                "Reset your Societix password",
                "Use this code to reset your password (valid for "
                        + RESET_TOKEN_TTL_MINUTES + " minutes): " + rawToken));
    }

    @Override
    @Transactional
    public void completeReset(String rawToken, String newPassword) {
        AccountToken token = resolveUsableToken(rawToken);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> invalidToken());
        // Applies the 12-128 policy; throws ValidationException on a bad password (Req 2.3).
        user.setPasswordHash(passwordService.hash(newPassword));
        userRepository.save(user);
        token.consume(now());
        tokenRepository.save(token);
    }

    private AccountToken resolveUsableToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        AccountToken token = tokenRepository
                .findByTokenHashAndType(SingleUseTokens.hash(rawToken), AccountTokenType.password_reset)
                .orElseThrow(() -> invalidToken());
        if (!token.isUsableAt(now())) {
            throw invalidToken(); // expired or already used: current password left unchanged (Req 2.6)
        }
        return token;
    }

    private static ValidationException invalidToken() {
        // Generic, non-enumerating message; does not distinguish unknown vs expired vs used.
        return new ValidationException("token", "invalid",
                "The reset link is invalid or has expired.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
