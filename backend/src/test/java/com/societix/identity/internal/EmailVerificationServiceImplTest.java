package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.common.api.DeliveryResult;
import com.societix.common.api.EmailMessage;
import com.societix.common.api.EmailSender;
import com.societix.common.api.ValidationException;
import com.societix.identity.api.EmailVerificationService;

/**
 * Unit tests for {@link EmailVerificationServiceImpl} (task 10.3; Req 2.14).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationServiceImpl")
class EmailVerificationServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;
    @Mock private AccountTokenRepository tokenRepository;

    private final CapturingEmailSender email = new CapturingEmailSender();
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl(userRepository, tokenRepository, email, clock);
        user = new User(EMAIL, "Test User");
        lenient().when(tokenRepository.save(any(AccountToken.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("requestVerification issues a single-use token and emails the raw value")
    void requestIssuesTokenAndEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        service.requestVerification(EMAIL);

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(AccountTokenType.email_verification);
        assertThat(email.messages).hasSize(1);
    }

    @Test
    @DisplayName("requestVerification for an unknown account is a safe no-op")
    void requestUnknownAccountIsSafe() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        service.requestVerification("ghost@example.com");
        verify(tokenRepository, never()).save(any());
        assertThat(email.messages).isEmpty();
    }

    @Test
    @DisplayName("confirmVerification with a valid token marks the email verified and consumes it")
    void confirmSucceeds() {
        assertThat(user.isEmailVerified()).isFalse();
        String raw = SingleUseTokens.generateRawToken();
        AccountToken token = new AccountToken(user.getId(), AccountTokenType.email_verification,
                SingleUseTokens.hash(raw), clock.instant().plus(Duration.ofMinutes(60)).atOffset(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndType(SingleUseTokens.hash(raw), AccountTokenType.email_verification))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.confirmVerification(raw);

        assertThat(user.isEmailVerified()).as("email must be verified after confirmation (Req 2.14)").isTrue();
        assertThat(token.getConsumedAt()).as("token must be consumed (single-use)").isNotNull();
    }

    @Test
    @DisplayName("confirmVerification with an expired token is rejected and email stays unverified")
    void confirmExpiredRejected() {
        String raw = SingleUseTokens.generateRawToken();
        AccountToken expired = new AccountToken(user.getId(), AccountTokenType.email_verification,
                SingleUseTokens.hash(raw), clock.instant().minus(Duration.ofMinutes(1)).atOffset(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndType(SingleUseTokens.hash(raw), AccountTokenType.email_verification))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.confirmVerification(raw)).isInstanceOf(ValidationException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    private static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new ArrayList<>();

        @Override
        public DeliveryResult send(EmailMessage message) {
            messages.add(message);
            return DeliveryResult.SENT;
        }
    }
}
