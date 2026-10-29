package com.societix.identity.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a single-use MFA recovery code (task 13.1; Req 24.5, 24.6). Only a one-way hash of
 * the code is stored; {@code consumedAt} enforces single use.
 */
@Entity
@Table(name = "mfa_recovery_codes")
public class MfaRecoveryCode {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false, insertable = false)
    private UUID id;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(UUID credentialId, String codeHash) {
        this.credentialId = credentialId;
        this.codeHash = codeHash;
    }

    public boolean isUsable() {
        return consumedAt == null;
    }

    public void consume(OffsetDateTime at) {
        this.consumedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }
}
