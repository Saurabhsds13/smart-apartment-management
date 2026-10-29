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
import com.societix.identity.api.EmailVerificationService;

/**
 * Default {@link EmailVerificationService} (task 10.3; Req 2.14).
 *
 * <p>{@link #requestVerification(String)} issues a single-use verification token (hash only)
 * expiring in {@value EmailVerificationService#VERIFICATION_TOKEN_TTL_MINUTES} minutes and emails
 * the raw token; it is a no-op for an unknown account. {@link #confirmVerification(String)}
 * validates the token (exists, unexpired, unconsumed), marks the account email verified, and
 * consumes the token. Until confirmed, the email is not treated as verified (Req 2.14).
 */
@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final AccountTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final Clock clock;

    public EmailVerificationServiceImpl(
            UserRepository userRepository,
            AccountTokenRepository tokenRepository,
            EmailSender emailSender,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requestVerification(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> account = userRepository.findByEmailIgnoreCase(email);
        if (account.isEmpty()) {
            return;
        }
        User user = account.get();
        String rawToken = SingleUseTokens.generateRawToken();
        OffsetDateTime expiresAt = now().plus(Duration.ofMinutes(VERIFICATION_TOKEN_TTL_MINUTES));
        tokenRepository.save(new AccountToken(
                user.getId(), AccountTokenType.email_verification, SingleUseTokens.hash(rawToken), expiresAt));
        emailSender.send(new EmailMessage(
                user.getEmail(),
                "Verify your Societix email",
                "Use this code to verify your email (valid for "
                        + VERIFICATION_TOKEN_TTL_MINUTES + " minutes): " + rawToken));
    }

    @Override
    @Transactional
    public void confirmVerification(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        AccountToken token = tokenRepository
                .findByTokenHashAndType(SingleUseTokens.hash(rawToken), AccountTokenType.email_verification)
                .orElseThrow(() -> invalidToken());
        if (!token.isUsableAt(now())) {
            throw invalidToken();
        }
        User user = userRepository.findById(token.getUserId()).orElseThrow(() -> invalidToken());
        user.setEmailVerified(true);
        userRepository.save(user);
        token.consume(now());
        tokenRepository.save(token);
    }

    private static ValidationException invalidToken() {
        return new ValidationException("token", "invalid",
                "The verification link is invalid or has expired.");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
