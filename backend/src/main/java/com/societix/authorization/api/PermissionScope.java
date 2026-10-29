package com.societix.authorization.api;

/**
 * The scope at which a role is granted a {@link Permission} in the Permission Matrix (task 12.1;
 * design.md "Role/permission model"; Req 3.7). Mirrors the matrix's scope abbreviations.
 */
public enum PermissionScope {
    /** Platform-wide (P) ? a Platform_Admin operation spanning the platform, not a single society. */
    PLATFORM,
    /** Own Society only (S) ? permitted within the actor's resolved Tenant_Context. */
    SOCIETY,
    /** Own Flat only (F) ? permitted only for objects belonging to the actor's own flat. */
    FLAT,
    /** Assigned scope (A) ? permitted only within an explicitly assigned subset (e.g. read-only auditor, vendor task). */
    ASSIGNED,
    /** Not permitted (the matrix dash) ? the role may not perform the operation. */
    NONE
}
