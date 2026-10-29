package com.societix.identity.api;

/**
 * The 11 platform roles (design.md "Data Models" &gt; user_society_memberships, ADR-4; Req 3.1).
 *
 * <p>These constants are the authoritative role set that the Permission Matrix maps to permissions
 * (encoded in the {@code authorization} module, task 12.1). The set is deliberately closed and is
 * mirrored by the {@code ck_memberships_role} CHECK constraint in {@code V4__users_memberships.sql},
 * so neither the application nor raw SQL can persist an unknown role.
 *
 * <p><strong>Persistence.</strong> Persisted by name via {@link jakarta.persistence.EnumType#STRING}
 * so the stored tokens match the uppercase names in the DB CHECK constraint exactly. The constants
 * are therefore UPPER_SNAKE_CASE to line up with those tokens.
 *
 * <p><strong>{@link #PLATFORM_ADMIN} note.</strong> A Platform_Admin has platform-wide scope and
 * holds <em>no</em> {@code user_society_memberships} row (its cross-tenant access runs through a
 * distinct audited path, Req 1.14, 3.5). The value exists in this set for completeness and for the
 * Permission Matrix, but it is not normally expressed as a per-society membership.
 */
public enum MembershipRole {

    /** Platform operator; platform-wide scope, not a per-society membership (Req 1.14, 3.5). */
    PLATFORM_ADMIN,

    /** Society administrator / managing committee; society-wide scope within one society. */
    SOCIETY_ADMIN,

    /** Manages facilities and bookings within the society. */
    FACILITY_MANAGER,

    /** Finance role: billing, payments, and financial reports within the society. */
    ACCOUNTANT,

    /** Gate/security staff: visitor entry/exit within the society. */
    SECURITY_GUARD,

    /** Flat owner resident. */
    OWNER,

    /** Tenant (renting) resident. */
    TENANT_RESIDENT,

    /** Family member associated under an owner/tenant resident. */
    FAMILY_MEMBER,

    /** Maintenance staff who service maintenance requests. */
    MAINTENANCE_STAFF,

    /** External vendor with a scoped, limited role. */
    VENDOR,

    /** Read-only auditor; produces no state change. */
    AUDITOR
}
