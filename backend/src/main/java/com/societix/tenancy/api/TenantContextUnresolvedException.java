package com.societix.tenancy.api;

/**
 * Thrown when {@link TenantContext#currentSocietyId()} is called but no owning Society is resolved
 * for the current request (Req 1.3, 1.6, BR1).
 *
 * <p>This signals one of two conditions:
 * <ul>
 *   <li>the request is <em>unresolved</em> — there is no authenticated session from which a Society
 *       could be derived (an anonymous/pre-authentication request); or</li>
 *   <li>the actor is a <em>Platform_Admin</em> acting at platform scope and therefore has no single
 *       owning Society.</li>
 * </ul>
 *
 * <p>For a tenant-owned operation this must be treated as an abort that yields zero tenant-owned
 * records rather than a fallback to an unscoped query (Req 1.6). It deliberately carries no Society
 * identifier and no actor detail so that handling it cannot leak whether a resource exists
 * (Req 1.7). The scoping layer (task 7.3) and the global exception handler translate it into a
 * safe, generic authorization response.
 */
public class TenantContextUnresolvedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception with a fixed, non-sensitive message. The message describes the
     * programming/condition state only; it never contains a Society id, actor id, or request data.
     */
    public TenantContextUnresolvedException() {
        super("No tenant context is resolved for the current request; "
                + "tenant-owned operations must not proceed.");
    }
}
