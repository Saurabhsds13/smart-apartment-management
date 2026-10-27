package com.societix.tenancy.internal;

import java.util.Optional;
import org.springframework.security.core.Authentication;

/**
 * Strategy that derives a tenant scope from the <strong>authenticated principal</strong> and
 * nothing else (Req 1.2, BR1). This is the single integration point through which the later
 * authentication wiring (task 10.1) and role/membership model (task 12.x) plug the authenticated
 * principal's owning Society (or platform-admin status) into the {@link TenantContextFilter},
 * without the filter needing to know the shape of the principal.
 *
 * <h2>Server-derived only</h2>
 * Implementations MUST derive the tenant scope exclusively from the {@link Authentication} (its
 * principal/authorities/credentials established server-side at sign-in). They MUST NOT read any
 * value from the HTTP request — no header, query parameter, path variable, or body — when deciding
 * the Society (Req 1.3, 1.4). The filter deliberately passes only the {@link Authentication} to
 * enforce this at the type level.
 */
interface TenantResolver {

    /**
     * Resolves the given authentication to a tenant scope.
     *
     * @param authentication the current authentication from the Spring {@code SecurityContext}; may
     *     be {@code null} for an anonymous/pre-authentication request
     * @return the resolved tenant scope, or {@link Optional#empty()} when the request is anonymous
     *     or carries no authenticated principal from which a Society can be derived (the
     *     {@link TenantContext} is then left unresolved and tenant-owned operations abort, Req 1.6)
     */
    Optional<AuthenticatedTenant> resolve(Authentication authentication);
}
