package com.societix.identity.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditService;
import com.societix.identity.api.MfaEnrollment;
import com.societix.identity.api.MfaService;

/**
 * Default {@link MfaService} backed by {@link TotpGenerator} (RFC 6238), {@link SecretEncryptor}
 * (AES-GCM secret-at-rest), and hashed single-use recovery codes (tasks 13.1, 13.2; Req 24.4?24.8).
 *
 * <p>The TOTP secret is encrypted before persistence and recovery codes are stored only as SHA-256
 * hashes, so a database read yields no usable second factor (Req 24.6). Enrollment and every
 * verification/step-up writes an audit record within the same transaction (Req 24.8), so MFA events
 * are attributable.
 */
@Service
public class MfaServiceImpl implements MfaService {

    private static final String ISSUER = "Societix";
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MfaCredentialRepository credentialRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final SecretEncryptor secretEncryptor;
    private final AuditService auditService;
    private final Clock clock;

    public MfaServiceImpl(
            MfaCredentialRepository credentialRepository,
            MfaRecoveryCodeRepository recoveryCodeRepository,
            SecretEncryptor secretEncryptor,
            AuditService auditService,
            Clock clock) {
        this.credentialRepository = credentialRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.secretEncryptor = secretEncryptor;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MfaEnrollment beginEnrollment(UUID userId, String accountLabel) {
        // Replace any prior (unconfirmed or old) enrollment: a fresh secret + recovery codes.
        credentialRepository.findByUserId(userId).ifPresent(credentialRepository::delete);

        String secret = TotpGenerator.generateSecret();
        MfaCredential credential =
                credentialRepository.save(new MfaCredential(userId, secretEncryptor.encrypt(secret)));

        List<String> rawRecoveryCodes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = generateRecoveryCode();
            rawRecoveryCodes.add(raw);
            recoveryCodeRepository.save(new MfaRecoveryCode(credential.getId(), SingleUseTokens.hash(raw)));
        }

        auditService.record(AuditEntry.builder()
                .action("MFA_ENROLLMENT_STARTED").targetType("MfaCredential")
                .actorUserId(userId).targetId(credential.getId().toString()).build());

        return new MfaEnrollment(secret, otpauthUri(accountLabel, secret), rawRecoveryCodes);
    }

    @Override
    @Transactional
    public boolean confirmEnrollment(UUID userId, String totpCode) {
        Optional<MfaCredential> found = credentialRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return false;
        }
        MfaCredential credential = found.get();
        String secret = secretEncryptor.decrypt(credential.getSecretEncrypted());
        if (!TotpGenerator.verify(secret, totpCode, clock.instant())) {
            return false;
        }
        credential.setEnabled(true);
        credentialRepository.save(credential);
        auditService.record(AuditEntry.builder()
                .action("MFA_ENABLED").targetType("MfaCredential")
                .actorUserId(userId).targetId(credential.getId().toString()).build());
        return true;
    }

    @Override
    @Transactional
    public boolean verify(UUID userId, String codeOrRecovery) {
        Optional<MfaCredential> found = credentialRepository.findByUserId(userId);
        if (found.isEmpty() || !found.get().isEnabled() || codeOrRecovery == null) {
            return false;
        }
        MfaCredential credential = found.get();

        // 1. Try as a TOTP code.
        String secret = secretEncryptor.decrypt(credential.getSecretEncrypted());
        if (TotpGenerator.verify(secret, codeOrRecovery.trim(), clock.instant())) {
            auditService.record(AuditEntry.builder()
                    .action("MFA_VERIFIED").targetType("MfaCredential")
                    .actorUserId(userId).targetId(credential.getId().toString()).build());
            return true;
        }

        // 2. Try as a single-use recovery code (consume on success; must belong to this credential).
        Optional<MfaRecoveryCode> recovery =
                recoveryCodeRepository.findByCodeHash(SingleUseTokens.hash(codeOrRecovery.trim()));
        if (recovery.isPresent()
                && recovery.get().getCredentialId().equals(credential.getId())
                && recovery.get().isUsable()) {
            recovery.get().consume(OffsetDateTime.now(clock));
            recoveryCodeRepository.save(recovery.get());
            auditService.record(AuditEntry.builder()
                    .action("MFA_RECOVERY_CODE_USED").targetType("MfaCredential")
                    .actorUserId(userId).targetId(credential.getId().toString()).build());
            return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEnrolled(UUID userId) {
        return credentialRepository.findByUserId(userId).map(MfaCredential::isEnabled).orElse(false);
    }

    /** Builds the otpauth:// provisioning URI an authenticator app scans. */
    private static String otpauthUri(String accountLabel, String secret) {
        String label = URLEncoder.encode(ISSUER + ":" + accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(ISSUER, StandardCharsets.UTF_8)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /** Generates a human-friendly single-use recovery code (10 hex chars). */
    private static String generateRecoveryCode() {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
