package com.societix.authorization.api;

/**
 * Server-side authorization checks enforcing the Permission Matrix at both the function level and
 * the object level (tasks 12.2, 12.3; design.md "Role/permission model"; Req 3.2, 3.3, 1.7, 18.3).
 *
 * <p>Every protected operation calls {@link #checkPermission} before executing (Req 3.2); a denial
 * throws {@link AuthorizationException} and the caller performs no partial state change (Req 3.3).
 * Any by-id access to a tenant-owned object additionally calls {@link #canAccessObject} so a
 * resource outside the actor's Society (or, for a flat-scoped role, another flat) is treated as
 * not-found rather than disclosed (Req 1.7).
 */
public interface AuthorizationService {

    /**
     * Authorizes {@code permission} for {@code actor} against the Permission Matrix. Succeeds only
     * if some role the actor holds grants the permission and the actor's resolved scope is
     * consistent with the granted {@link PermissionScope} (e.g. a SOCIETY grant requires a resolved
     * society; a PLATFORM grant requires platform-admin).
     *
     * @param actor      the server-derived authenticated actor
     * @param permission the operation being attempted
     * @throws AuthorizationException if the actor may not perform the operation (Req 3.3)
     */
    void checkPermission(Actor actor, Permission permission);

    /**
     * Object-level (BOLA/IDOR) check: whether {@code actor} may act on the given tenant-owned
     * {@code object} for {@code permission}. The object must belong to the actor's Society; for a
     * grant that is FLAT-scoped only, the object's owning flat must be one of the actor's flats
     * (Req 18.3). A Platform_Admin is allowed only through the audited platform path, not here.
     *
     * @param actor      the server-derived authenticated actor
     * @param permission the operation being attempted on the object
     * @param object     the tenant-owned target
     * @return {@code true} if access is allowed; {@code false} if it must be treated as not-found (Req 1.7)
     */
    boolean canAccessObject(Actor actor, Permission permission, TenantOwned object);
}
