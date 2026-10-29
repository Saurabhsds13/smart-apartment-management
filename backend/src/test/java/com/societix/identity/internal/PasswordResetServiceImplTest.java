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
import java.util.UUID;

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
import com.societix.identity.api.PasswordResetService;

/**
 * Unit tests for {@link PasswordResetServiceImpl} (task 10.3; Req 2.5, 2.6). Uses mocked
 * repositories, a capturing {@link EmailSender}, a fixed {@link Clock}, and a real
 * {@link PasswordServiceImpl}, so token issuance, single-use consumption, expiry/used rejection,
 * and enumeration-safety are all asserted without a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetServiceImpl")
class PasswordResetServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock private UserRepository userRepository;
    @Mock private AccountTokenRepository tokenRepository;

    private final CapturingEmailSender email = new CapturingEmailSender();
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetServiceImpl(
                userRepository, tokenRepository,
                new PasswordServiceImpl(new Sha256BCryptPasswordEncoder(4)), email, clock);
        user = new User(EMAIL, "Test User");
        user.setPasswordHash("original-hash");
        lenient().when(tokenRepository.save(any(AccountToken.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("requestReset for a known account stores a hashed token and emails the raw token")
    void requestResetIssuesTokenAndEmail() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));

        service.requestReset(EMAIL);

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(tokenRepository).save(saved.capture());
        AccountToken token = saved.getValue();
        assertThat(token.getType()).isEqualTo(AccountTokenType.password_reset);
        assertThat(token.getExpiresAt())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(60)).atOffset(ZoneOffset.UTC));
        // The emailed raw token must NOT equal the stored hash (only the hash is persisted).
        assertThat(email.messages).hasSize(1);
        String rawInEmail = extractToken(email.messages.get(0).body());
        assertThat(token.getTokenHash())
                .isEqualTo(SingleUseTokens.hash(rawInEmail))
                .isNotEqualTo(rawInEmail);
    }

    @Test
    @DisplayName("requestReset for an unknown account is enumeration-safe: no token, no email")
    void requestResetUnknownAccountIsSafe() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.requestReset("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        assertThat(email.messages).isEmpty();
    }

    @Test
    @DisplayName("completeReset with a valid token updates the password and consumes the token")
    void completeResetSucceedsAndConsumes() {
        String raw = SingleUseTokens.generateRawToken();
        AccountToken token = new AccountToken(user.getId(), AccountTokenType.password_reset,
                SingleUseTokens.hash(raw), clock.instant().plus(Duration.ofMinutes(60)).atOffset(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndType(SingleUseTokens.hash(raw), AccountTokenType.password_reset))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.completeReset(raw, "a-brand-new-password");

        assertThat(user.getPasswordHash())
                .as("password hash must change to the new (adaptive) hash")
                .isNotEqualTo("original-hash");
        assertThat(token.getConsumedAt()).as("token must be consumed (single-use)").isNotNull();
    }

    @Test
    @DisplayName("completeReset with an expired token is rejected and leaves the password unchanged (Req 2.6)")
    void completeResetExpiredTokenRejected() {
        String raw = SingleUseTokens.generateRawToken();
        AccountToken expired = new AccountToken(user.getId(), AccountTokenType.password_reset,
                SingleUseTokens.hash(raw), clock.instant().minus(Duration.ofMinutes(1)).atOffset(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndType(SingleUseTokens.hash(raw), AccountTokenType.password_reset))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.completeReset(raw, "a-brand-new-password"))
                .isInstanceOf(ValidationException.class);
        assertThat(user.getPasswordHash()).isEqualTo("original-hash");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeReset with an already-consumed token is rejected (single-use, Req 2.6)")
    void completeResetConsumedTokenRejected() {
        String raw = SingleUseTokens.generateRawToken();
        AccountToken consumed = new AccountToken(user.getId(), AccountTokenType.password_reset,
                SingleUseTokens.hash(raw), clock.instant().plus(Duration.ofMinutes(60)).atOffset(ZoneOffset.UTC));
        consumed.consume(clock.instant().minus(Duration.ofSeconds(5)).atOffset(ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndType(SingleUseTokens.hash(raw), AccountTokenType.password_reset))
                .thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.completeReset(raw, "a-brand-new-password"))
                .isInstanceOf(ValidationException.class);
        assertThat(user.getPasswordHash()).isEqualTo("original-hash");
    }

    @Test
    @DisplayName("completeReset with an unknown token is rejected")
    void completeResetUnknownTokenRejected() {
        when(tokenRepository.findByTokenHashAndType(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeReset("nope", "a-brand-new-password"))
                .isInstanceOf(ValidationException.class);
    }

    private static String extractToken(String body) {
        int idx = body.lastIndexOf(": ");
        return body.substring(idx + 2).trim();
    }

    /** Captures sent messages so the test can inspect the emailed raw token. */
    private static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new ArrayList<>();

        @Override
        public DeliveryResult send(EmailMessage message) {
            messages.add(message);
            return DeliveryResult.SENT;
        }
    }
}
