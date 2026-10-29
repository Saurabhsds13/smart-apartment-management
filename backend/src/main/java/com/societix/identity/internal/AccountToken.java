package com.societix.identity.internal;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for a single-use, time-limited account credential (task 10.3; design.md "Identity &
 * Auth"; Req 2.5, 2.6, 2.14).
 *
 * <p>Only the one-way {@code tokenHash} of the credential is stored; the raw token is emailed to
 * the user and never persisted, so a database read cannot recover a usable credential. A token is
 * valid only while {@code now() < expiresAt} and {@code consumedAt IS NULL}; consuming it stamps
 * {@code consumedAt} to enforce single use (Req 2.6). Platform-level (keyed to a platform-level
 * {@code users} row): no {@code society_id}, not subject to tenant RLS.
 */
@Entity
@Table(name = "account_tokens")
public class AccountToken {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false, insertable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountTokenType type;

    /** One-way hash of the raw single-use token (never the raw token itself). */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Set on first successful use to enforce single-use; {@code null} while still usable. */
    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    protected AccountToken() {
        // for JPA
    }

    public AccountToken(UUID userId, AccountTokenType type, String tokenHash, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** True if the token is neither consumed nor past its expiry at the given instant. */
    public boolean isUsableAt(OffsetDateTime now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    public void consume(OffsetDateTime at) {
        this.consumedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AccountTokenType getType() {
        return type;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
