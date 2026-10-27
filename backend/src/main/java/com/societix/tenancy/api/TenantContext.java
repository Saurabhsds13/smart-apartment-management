package com.societix.tenancy.api;

import java.util.UUID;

/**
 * Request-scoped holder for the authenticated actor's <strong>tenant scope</strong> (the owning
 * Society) for the current request (design.md "TenantContext and Tenant Scoping", ADR-2;
 * Req 1.2, 1.3, BR1).
 *
 * <h2>Server-derived only</h2>
 * The context is populated <strong>exclusively</strong> from the authenticated server-side session
 * (the Spring {@code SecurityContext} principal), never from client input — not from a request
 * header, query parameter, path variable, or body (Req 1.2, BR1). This is the single source of
 * truth other modules consult to scope every tenant-owned data access, so a client can never widen
 * or change its own tenant scope by supplying a society identifier (Req 1.3).
 *
 * <h2>Resolution states</h2>
 * A request is in exactly one of three states:
 * <ul>
 *   <li><strong>Unresolved</strong> — no authenticated session from which a Society can be derived
 *       (an anonymous/pre-authentication request). {@link #isResolved()} is {@code false} and
 *       {@link #currentSocietyId()} throws {@link TenantContextUnresolvedException}. Tenant-owned
 *       operations must abort and return zero rows in this state (Req 1.6; enforced by the scoping
 *       layer in task 7.3).</li>
 *   <li><strong>Resolved to a Society</strong> — the principal is a member of exactly one Society
 *       scope for this request. {@link #isResolved()} is {@code true} and {@link #currentSocietyId()}
 *       returns that Society id.</li>
 *   <li><strong>Platform-admin</strong> — the principal is a Platform_Admin acting at platform
 *       scope with no single owning Society. {@link #isPlatformAdmin()} is {@code true}. A
 *       Platform_Admin is considered {@link #isResolved() resolved} for request handling, but has no
 *       implicit society; cross-tenant access runs only through a distinct, audited path
 *       (Req 1.14, 3.5) and never by calling {@link #currentSocietyId()} here.</li>
 * </ul>
 *
 * <p>Implementations are request-scoped and must not be cached across requests; a role or
 * membership change takes effect on the next request's resolution (Edge: role changes re-evaluated
 * per request).
 */
public interface TenantContext {

    /**
     * Returns the owning Society id for the current request.
     *
     * @return the resolved Society id (never {@code null})
     * @throws TenantContextUnresolvedException if no Society is resolved for this request — i.e.
     *     the request is unresolved (anonymous) or the actor is a Platform_Admin acting at platform
     *     scope with no single owning Society. Callers performing a tenant-owned operation must
     *     treat this as an abort that yields zero tenant-owned records (Req 1.6).
     */
    UUID currentSocietyId();

    /**
     * Indicates whether the current actor is a Platform_Admin acting at platform scope.
     *
     * <p>Platform scope grants no implicit membership in any Society's resident-facing data; any
     * cross-tenant operation must go through an explicit, audited path (Req 1.14, 3.5). This flag
     * lets the scoping layer distinguish "no society because unauthenticated" from "no society
     * because platform-scoped".
     *
     * @return {@code true} if the actor is a platform administrator, otherwise {@code false}
     */
    boolean isPlatformAdmin();

    /**
     * Indicates whether a tenant scope has been resolved for the current request.
     *
     * <p>Returns {@code true} when the request carries an authenticated principal from which a
     * Society scope (or platform scope) was derived; {@code false} for an anonymous/pre-authentication
     * request. The tenant-scoping layer uses this to decide whether a tenant-owned operation may
     * proceed at all (Req 1.6).
     *
     * @return {@code true} if resolved from the authenticated session, otherwise {@code false}
     */
    boolean isResolved();
}
