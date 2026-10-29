package com.societix.identity.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a user's TOTP MFA enrollment (task 13.1; Req 24.4, 24.6). The shared secret is
 * stored encrypted at rest ({@code secretEncrypted}); it is never persisted or logged in plaintext.
 * {@code enabled} is false until the user confirms a first valid code (enrollment completed).
 * Platform-level (keyed to users): no society_id, not subject to tenant RLS.
 */
@Entity
@Table(name = "mfa_credentials")
public class MfaCredential {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false, insertable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false)
    private String type = "totp";

    /** TOTP shared secret, encrypted at rest (never plaintext, Req 24.6). */
    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime updatedAt;

    protected MfaCredential() {
    }

    public MfaCredential(UUID userId, String secretEncrypted) {
        this.userId = userId;
        this.secretEncrypted = secretEncrypted;
        this.type = "totp";
        this.enabled = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public void setSecretEncrypted(String secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
