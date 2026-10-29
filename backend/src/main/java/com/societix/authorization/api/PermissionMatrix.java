package com.societix.authorization.api;

import java.util.EnumMap;
import java.util.Map;

import com.societix.identity.api.MembershipRole;

/**
 * The authoritative, immutable encoding of the Permission Matrix from the requirements
 * (task 12.1; design.md "Role/permission model"; Req 3.1, 3.7, 3.8).
 *
 * <p>For every (role, permission) pair this maps the {@link PermissionScope} at which the role may
 * perform the operation, or {@link PermissionScope#NONE} when the matrix shows a dash. It is the
 * single source of truth the authorization checks consult (tasks 12.2, 12.3): no call site encodes
 * its own role list. The table below mirrors the requirements' "Permission Matrix" section
 * row-for-row; any divergence is a bug.
 *
 * <p>Scope semantics follow the matrix abbreviations: {@code PLATFORM} = platform-wide (P),
 * {@code SOCIETY} = own society (S), {@code FLAT} = own flat (F), {@code ASSIGNED} = assigned subset
 * (A), {@code NONE} = not permitted. Note that a {@code PLATFORM}-scoped Platform_Admin grant on a
 * report/audit/privacy row corresponds to the matrix's "P (audited)" ? the audit obligation is
 * enforced by the platform-admin drill-down path (Req 1.14, 13.5, 16.5), not weakened here.
 */
public final class PermissionMatrix {

    /** role -> (permission -> scope); every role present, defaulting to NONE where unspecified. */
    private static final Map<MembershipRole, Map<Permission, PermissionScope>> MATRIX = build();

    private PermissionMatrix() {
    }

    /**
     * Returns the scope at which {@code role} may perform {@code permission}, or
     * {@link PermissionScope#NONE} if the role is not permitted the operation at all.
     *
     * @param role       the actor's role (must not be {@code null})
     * @param permission the operation (must not be {@code null})
     * @return the granted scope, never {@code null}
     */
    public static PermissionScope scopeFor(MembershipRole role, Permission permission) {
        return MATRIX.getOrDefault(role, Map.of()).getOrDefault(permission, PermissionScope.NONE);
    }

    /**
     * Convenience predicate: whether {@code role} is permitted {@code permission} at any scope.
     *
     * @param role       the actor's role
     * @param permission the operation
     * @return {@code true} if the granted scope is not {@link PermissionScope#NONE}
     */
    public static boolean isPermitted(MembershipRole role, Permission permission) {
        return scopeFor(role, permission) != PermissionScope.NONE;
    }

    // ------------------------------------------------------------------
    // Matrix construction ? mirrors requirements "Permission Matrix" row for row.
    // ------------------------------------------------------------------

    private static Map<MembershipRole, Map<Permission, PermissionScope>> build() {
        Map<MembershipRole, Map<Permission, PermissionScope>> m = new EnumMap<>(MembershipRole.class);
        for (MembershipRole role : MembershipRole.values()) {
            m.put(role, new EnumMap<>(Permission.class));
        }

        // Scope shorthands.
        final PermissionScope P = PermissionScope.PLATFORM;
        final PermissionScope S = PermissionScope.SOCIETY;
        final PermissionScope F = PermissionScope.FLAT;
        final PermissionScope A = PermissionScope.ASSIGNED;

        // Onboard/suspend Society | P only
        grant(m, Permission.SOCIETY_LIFECYCLE_MANAGE, MembershipRole.PLATFORM_ADMIN, P);
        // Platform metrics (aggregate) | P only
        grant(m, Permission.PLATFORM_METRICS_VIEW, MembershipRole.PLATFORM_ADMIN, P);
        // Manage Society config | Society_Admin S
        grant(m, Permission.SOCIETY_CONFIG_MANAGE, MembershipRole.SOCIETY_ADMIN, S);
        // Manage buildings/flats | Society_Admin S, Facility_Manager S
        grant(m, Permission.PROPERTY_STRUCTURE_MANAGE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.PROPERTY_STRUCTURE_MANAGE, MembershipRole.FACILITY_MANAGER, S);
        // Manage residents | Society_Admin S
        grant(m, Permission.RESIDENT_MANAGE, MembershipRole.SOCIETY_ADMIN, S);
        // Manage family members | Owner F, Tenant_Resident F
        grant(m, Permission.FAMILY_MEMBER_MANAGE, MembershipRole.OWNER, F);
        grant(m, Permission.FAMILY_MEMBER_MANAGE, MembershipRole.TENANT_RESIDENT, F);
        // Assign roles (within Society) | Society_Admin S
        grant(m, Permission.ROLE_ASSIGN, MembershipRole.SOCIETY_ADMIN, S);
        // Publish announcements | Society_Admin S, Facility_Manager S
        grant(m, Permission.ANNOUNCEMENT_PUBLISH, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.ANNOUNCEMENT_PUBLISH, MembershipRole.FACILITY_MANAGER, S);
        // View announcements | SA S, FM S, Accountant S, Security S, Owner F, Tenant F, Family F, Auditor A
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.ACCOUNTANT, S);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.SECURITY_GUARD, S);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.OWNER, F);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.TENANT_RESIDENT, F);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.FAMILY_MEMBER, F);
        grant(m, Permission.ANNOUNCEMENT_VIEW, MembershipRole.AUDITOR, A);
        // Raise maintenance request | SA S, FM S, Owner F, Tenant F, Family F
        grant(m, Permission.MAINTENANCE_REQUEST_RAISE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.MAINTENANCE_REQUEST_RAISE, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.MAINTENANCE_REQUEST_RAISE, MembershipRole.OWNER, F);
        grant(m, Permission.MAINTENANCE_REQUEST_RAISE, MembershipRole.TENANT_RESIDENT, F);
        grant(m, Permission.MAINTENANCE_REQUEST_RAISE, MembershipRole.FAMILY_MEMBER, F);
        // Assign/resolve maintenance request | SA S, FM S, Maintenance_Staff S (assigned)
        grant(m, Permission.MAINTENANCE_REQUEST_ASSIGN, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.MAINTENANCE_REQUEST_ASSIGN, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.MAINTENANCE_REQUEST_ASSIGN, MembershipRole.MAINTENANCE_STAFF, A);
        // Update maintenance status | SA S, FM S, Maintenance_Staff S (assigned)
        grant(m, Permission.MAINTENANCE_REQUEST_UPDATE_STATUS, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.MAINTENANCE_REQUEST_UPDATE_STATUS, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.MAINTENANCE_REQUEST_UPDATE_STATUS, MembershipRole.MAINTENANCE_STAFF, A);
        // Configure charge rules | SA S, Accountant S
        grant(m, Permission.CHARGE_RULE_CONFIGURE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.CHARGE_RULE_CONFIGURE, MembershipRole.ACCOUNTANT, S);
        // Generate invoices | SA S, Accountant S
        grant(m, Permission.INVOICE_GENERATE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.INVOICE_GENERATE, MembershipRole.ACCOUNTANT, S);
        // Record payment / issue receipt | SA S, Accountant S
        grant(m, Permission.PAYMENT_RECORD, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.PAYMENT_RECORD, MembershipRole.ACCOUNTANT, S);
        // Record payment adjustments/credits/refunds | SA S, Accountant S
        grant(m, Permission.PAYMENT_ADJUST, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.PAYMENT_ADJUST, MembershipRole.ACCOUNTANT, S);
        // View own dues/invoices | SA S, Accountant S, Owner F, Tenant F, Auditor A
        grant(m, Permission.DUES_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.DUES_VIEW, MembershipRole.ACCOUNTANT, S);
        grant(m, Permission.DUES_VIEW, MembershipRole.OWNER, F);
        grant(m, Permission.DUES_VIEW, MembershipRole.TENANT_RESIDENT, F);
        grant(m, Permission.DUES_VIEW, MembershipRole.AUDITOR, A);
        // Record visitor entry/exit | SA S, FM S, Security S
        grant(m, Permission.VISITOR_RECORD, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.VISITOR_RECORD, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.VISITOR_RECORD, MembershipRole.SECURITY_GUARD, S);
        // Approve/deny visit | Owner F, Tenant F
        grant(m, Permission.VISIT_DECIDE, MembershipRole.OWNER, F);
        grant(m, Permission.VISIT_DECIDE, MembershipRole.TENANT_RESIDENT, F);
        // View visitor log | SA S, FM S, Security S, Auditor A
        grant(m, Permission.VISITOR_LOG_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.VISITOR_LOG_VIEW, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.VISITOR_LOG_VIEW, MembershipRole.SECURITY_GUARD, S);
        grant(m, Permission.VISITOR_LOG_VIEW, MembershipRole.AUDITOR, A);
        // Configure facilities | SA S, FM S
        grant(m, Permission.FACILITY_CONFIGURE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.FACILITY_CONFIGURE, MembershipRole.FACILITY_MANAGER, S);
        // Create/cancel booking | SA S, FM S, Owner F, Tenant F
        grant(m, Permission.BOOKING_MANAGE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.BOOKING_MANAGE, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.BOOKING_MANAGE, MembershipRole.OWNER, F);
        grant(m, Permission.BOOKING_MANAGE, MembershipRole.TENANT_RESIDENT, F);
        // Approve/reject booking | SA S, FM S
        grant(m, Permission.BOOKING_APPROVE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.BOOKING_APPROVE, MembershipRole.FACILITY_MANAGER, S);
        // View bookings | SA S, FM S, Owner F, Tenant F, Family F, Auditor A
        grant(m, Permission.BOOKING_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.BOOKING_VIEW, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.BOOKING_VIEW, MembershipRole.OWNER, F);
        grant(m, Permission.BOOKING_VIEW, MembershipRole.TENANT_RESIDENT, F);
        grant(m, Permission.BOOKING_VIEW, MembershipRole.FAMILY_MEMBER, F);
        grant(m, Permission.BOOKING_VIEW, MembershipRole.AUDITOR, A);
        // Generate reports | Platform_Admin P (audited), SA S, FM S, Accountant S, Auditor A
        grant(m, Permission.REPORT_GENERATE, MembershipRole.PLATFORM_ADMIN, P);
        grant(m, Permission.REPORT_GENERATE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.REPORT_GENERATE, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.REPORT_GENERATE, MembershipRole.ACCOUNTANT, S);
        grant(m, Permission.REPORT_GENERATE, MembershipRole.AUDITOR, A);
        // View audit trail | Platform_Admin P (audited), SA S, Accountant S (financial), Auditor A
        grant(m, Permission.AUDIT_TRAIL_VIEW, MembershipRole.PLATFORM_ADMIN, P);
        grant(m, Permission.AUDIT_TRAIL_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.AUDIT_TRAIL_VIEW, MembershipRole.ACCOUNTANT, S);
        grant(m, Permission.AUDIT_TRAIL_VIEW, MembershipRole.AUDITOR, A);
        // Manage MFA settings | Platform_Admin P, SA S, Owner F, Tenant F
        grant(m, Permission.MFA_MANAGE, MembershipRole.PLATFORM_ADMIN, P);
        grant(m, Permission.MFA_MANAGE, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.MFA_MANAGE, MembershipRole.OWNER, F);
        grant(m, Permission.MFA_MANAGE, MembershipRole.TENANT_RESIDENT, F);
        // Handle data-principal/privacy requests | Platform_Admin P (audited), SA S
        grant(m, Permission.PRIVACY_REQUEST_HANDLE, MembershipRole.PLATFORM_ADMIN, P);
        grant(m, Permission.PRIVACY_REQUEST_HANDLE, MembershipRole.SOCIETY_ADMIN, S);
        // Vendor scoped task view | SA S (grants), FM S (grants), Vendor A
        grant(m, Permission.VENDOR_TASK_VIEW, MembershipRole.SOCIETY_ADMIN, S);
        grant(m, Permission.VENDOR_TASK_VIEW, MembershipRole.FACILITY_MANAGER, S);
        grant(m, Permission.VENDOR_TASK_VIEW, MembershipRole.VENDOR, A);

        // Freeze into immutable maps.
        Map<MembershipRole, Map<Permission, PermissionScope>> frozen = new EnumMap<>(MembershipRole.class);
        m.forEach((role, perms) -> frozen.put(role, Map.copyOf(perms)));
        return Map.copyOf(frozen);
    }

    private static void grant(
            Map<MembershipRole, Map<Permission, PermissionScope>> m,
            Permission permission, MembershipRole role, PermissionScope scope) {
        m.get(role).put(permission, scope);
    }
}
