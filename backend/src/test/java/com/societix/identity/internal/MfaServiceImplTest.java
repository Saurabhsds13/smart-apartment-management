package com.societix.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditService;
import com.societix.identity.api.MfaEnrollment;
import com.societix.identity.api.MfaService;

/**
 * Unit tests for {@link MfaServiceImpl} (tasks 13.1, 13.2; Req 24.4-24.8). Uses mocked repositories
 * (with an in-memory store), a real {@link SecretEncryptor} and {@link TotpGenerator}, a mocked
 * {@link AuditService}, and a fixed {@link Clock}, so enrollment, confirmation, TOTP + recovery
 * verification, single-use consumption, secret-never-plaintext, and audit emission are all
 * asserted without a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MfaServiceImpl")
class MfaServiceImplTest {

    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final UUID USER = UUID.randomUUID();

    @Mock private MfaCredentialRepository credentialRepository;
    @Mock private MfaRecoveryCodeRepository recoveryCodeRepository;
    @Mock private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC);
    private MfaService service;

    // In-memory stand-ins so saved entities are retrievable within a test.
    private final Map<UUID, MfaCredential> creds = new HashMap<>();
    private final Map<String, MfaRecoveryCode> recoveryByHash = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new MfaServiceImpl(credentialRepository, recoveryCodeRepository,
                new SecretEncryptor(KEY), auditService, clock);

        lenient().when(credentialRepository.save(any(MfaCredential.class))).thenAnswer(inv -> {
            MfaCredential c = inv.getArgument(0);
            // Assign an id on first save if absent (the real DB default does this).
            if (c.getId() == null) {
                setId(c);
            }
            creds.put(c.getId(), c);
            byUser.put(c.getUserId(), c);
            return c;
        });
        lenient().when(credentialRepository.findByUserId(any()))
                .thenAnswer(inv -> Optional.ofNullable(byUser.get(inv.getArgument(0))));
        lenient().when(recoveryCodeRepository.save(any(MfaRecoveryCode.class))).thenAnswer(inv -> {
            MfaRecoveryCode r = inv.getArgument(0);
            recoveryByHash.put(r.getCodeHash(), r);
            return r;
        });
        lenient().when(recoveryCodeRepository.findByCodeHash(any()))
                .thenAnswer(inv -> Optional.ofNullable(recoveryByHash.get(inv.getArgument(0))));
    }

    private final Map<UUID, MfaCredential> byUser = new HashMap<>();

    @Test
    @DisplayName("beginEnrollment returns a secret + recovery codes and stores the secret encrypted")
    void beginEnrollmentStoresEncryptedSecret() {
        MfaEnrollment enrollment = service.beginEnrollment(USER, "user@example.com");

        assertThat(enrollment.secret()).isNotBlank();
        assertThat(enrollment.otpauthUri()).contains("otpauth://totp/").contains("Societix");
        assertThat(enrollment.recoveryCodes()).hasSize(10);

        MfaCredential stored = byUser.get(USER);
        assertThat(stored).isNotNull();
        assertThat(stored.isEnabled()).as("not enabled until confirmed").isFalse();
        // The stored secret is encrypted: it must not equal (or contain) the raw secret.
        assertThat(stored.getSecretEncrypted())
                .isNotEqualTo(enrollment.secret())
                .doesNotContain(enrollment.secret());
        verify(auditService, atLeastOnce()).record(any(AuditEntry.class));
    }

    @Test
    @DisplayName("confirmEnrollment with a valid current code enables MFA")
    void confirmEnablesMfa() {
        MfaEnrollment enrollment = service.beginEnrollment(USER, "user@example.com");
        String code = currentCode(enrollment.secret());

        boolean confirmed = service.confirmEnrollment(USER, code);

        assertThat(confirmed).isTrue();
        assertThat(service.isEnrolled(USER)).isTrue();
    }

    @Test
    @DisplayName("confirmEnrollment with a wrong code does not enable MFA")
    void confirmRejectsWrongCode() {
        service.beginEnrollment(USER, "user@example.com");
        assertThat(service.confirmEnrollment(USER, "000000")).isFalse();
        assertThat(service.isEnrolled(USER)).isFalse();
    }

    @Test
    @DisplayName("verify accepts a valid TOTP code for an enabled enrollment")
    void verifyAcceptsTotp() {
        MfaEnrollment enrollment = enrollAndEnable();
        assertThat(service.verify(USER, currentCode(enrollment.secret()))).isTrue();
    }

    @Test
    @DisplayName("verify accepts a recovery code once, then rejects its reuse (single-use, Req 24.5)")
    void recoveryCodeIsSingleUse() {
        MfaEnrollment enrollment = enrollAndEnable();
        String recovery = enrollment.recoveryCodes().get(0);

        assertThat(service.verify(USER, recovery)).as("first use accepted").isTrue();
        assertThat(service.verify(USER, recovery)).as("reuse rejected (consumed)").isFalse();
    }

    @Test
    @DisplayName("verify rejects when MFA is not enabled, and for a wrong code")
    void verifyRejectsWhenNotEnabledOrWrong() {
        // Not enrolled at all.
        assertThat(service.verify(USER, "123456")).isFalse();
        // Enrolled but not confirmed/enabled.
        service.beginEnrollment(USER, "user@example.com");
        assertThat(service.verify(USER, "123456")).isFalse();
    }

    private MfaEnrollment enrollAndEnable() {
        MfaEnrollment enrollment = service.beginEnrollment(USER, "user@example.com");
        service.confirmEnrollment(USER, currentCode(enrollment.secret()));
        return enrollment;
    }

    /** Deterministic exact-step TOTP code valid at the fixed clock instant (test-only). */
    private String currentCode(String secret) {
        return TotpGenerator.codeAt(secret, clock.instant());
    }

    /** Reflectively assign the DB-generated id on a saved credential for the in-memory stub. */
    private static void setId(MfaCredential c) {
        try {
            var f = MfaCredential.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
