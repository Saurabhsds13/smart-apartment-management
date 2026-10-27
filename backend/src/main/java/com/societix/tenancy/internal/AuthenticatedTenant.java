package com.societix.tenancy.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable result of resolving an authenticated principal to a tenant scope
 * (see {@link TenantResolver}). This is the single value type the {@link TenantContextFilter}
 * consumes, so the plumbing stays agnostic of how the authenticated principal is shaped — a later
 * task (10.1/12.x) can change the principal model and only touch the resolver.
 *
 * <p>Exactly one of the two resolved forms applies:
 * <ul>
 *   <li>{@link #ofSociety(UUID)} — the actor acts within one owning Society;</li>
 *   <li>{@link #platformAdmin()} — the actor is a Platform_Admin at platform scope (no society).</li>
 * </ul>
 */
final class AuthenticatedTenant {

    private final UUID societyId;
    private final boolean platformAdmin;

    private AuthenticatedTenant(UUID societyId, boolean platformAdmin) {
        this.societyId = societyId;
        this.platformAdmin = platformAdmin;
    }

    /**
     * Resolves to a specific owning Society.
     *
     * @param societyId the server-derived Society id (never {@code null})
     */
    static AuthenticatedTenant ofSociety(UUID societyId) {
        return new AuthenticatedTenant(Objects.requireNonNull(societyId, "societyId"), false);
    }

    /** Resolves to platform-admin scope (no single owning Society). */
    static AuthenticatedTenant platformAdmin() {
        return new AuthenticatedTenant(null, true);
    }

    boolean isPlatformAdmin() {
        return platformAdmin;
    }

    /** The owning Society id, present only when not a platform admin. */
    Optional<UUID> societyId() {
        return Optional.ofNullable(societyId);
    }
}
