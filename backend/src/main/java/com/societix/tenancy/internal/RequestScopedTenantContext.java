package com.societix.tenancy.internal;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped implementation of {@link TenantContext} (design.md "TenantContext and Tenant
 * Scoping", ADR-2; Req 1.2, 1.3, BR1).
 *
 * <p>One instance exists per HTTP request. It starts <em>unresolved</em> and is populated exactly
 * once by {@link TenantContextFilter} <strong>after</strong> authentication, from the authenticated
 * principal only — never from client input. Because the bean is request-scoped, it carries no state
 * between requests, so a pooled worker thread can never leak one request's society into the next,
 * and a membership/role change takes effect on the next request's resolution.
 *
 * <h2>Scoped proxy</h2>
 * The bean is exposed through a {@link ScopedProxyMode#TARGET_CLASS CGLIB scoped proxy} so that
 * singletons (services, repositories) can hold a single injected reference and still observe the
 * correct per-request instance on each call. Injecting the {@link TenantContext} interface elsewhere
 * resolves to this proxy.
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedTenantContext implements TenantContext {

    /**
     * Mutually exclusive resolution states for a single request. A fresh instance begins
     * {@link #UNRESOLVED}; the filter transitions it exactly once.
     */
    private enum State {
        /** No authenticated session from which a Society could be derived (anonymous request). */
        UNRESOLVED,
        /** Authenticated member acting within exactly one owning Society. */
        SOCIETY,
        /** Authenticated Platform_Admin acting at platform scope with no single owning Society. */
        PLATFORM_ADMIN
    }

    private State state = State.UNRESOLVED;

    /** Non-null only in {@link State#SOCIETY}. */
    private UUID societyId;

    @Override
    public UUID currentSocietyId() {
        if (state != State.SOCIETY) {
            // Unresolved (anonymous) or platform-admin: there is no single owning society to scope
            // to. Callers performing a tenant-owned operation must abort and return zero rows.
            throw new TenantContextUnresolvedException();
        }
        return societyId;
    }

    @Override
    public boolean isPlatformAdmin() {
        return state == State.PLATFORM_ADMIN;
    }

    @Override
    public boolean isResolved() {
        return state != State.UNRESOLVED;
    }

    /**
     * Resolves this context to a specific owning Society. Package-private: only the tenancy module's
     * {@link TenantContextFilter} may populate the context, and only from the authenticated
     * principal (Req 1.2, BR1). May be called at most once per request.
     *
     * @param societyId the server-derived owning Society id (never {@code null})
     * @throws IllegalStateException if this context has already been resolved for the request
     */
    void resolveSociety(UUID societyId) {
        ensureUnresolved();
        this.societyId = Objects.requireNonNull(societyId, "societyId must not be null");
        this.state = State.SOCIETY;
    }

    /**
     * Resolves this context to platform-admin (platform scope, no single owning Society).
     * Package-private; see {@link #resolveSociety(UUID)}. May be called at most once per request.
     *
     * @throws IllegalStateException if this context has already been resolved for the request
     */
    void resolvePlatformAdmin() {
        ensureUnresolved();
        this.societyId = null;
        this.state = State.PLATFORM_ADMIN;
    }

    private void ensureUnresolved() {
        if (state != State.UNRESOLVED) {
            // Defensive: resolution must happen once, in the filter. A second attempt indicates a
            // wiring bug (e.g. the filter running twice) and must fail loudly rather than silently
            // overwrite an already-resolved tenant scope.
            throw new IllegalStateException(
                    "TenantContext has already been resolved for this request");
        }
    }
}
