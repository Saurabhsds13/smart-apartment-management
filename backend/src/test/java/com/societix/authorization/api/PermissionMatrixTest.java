package com.societix.authorization.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.societix.identity.api.MembershipRole;

/**
 * Verifies {@link PermissionMatrix} encodes the authoritative Permission Matrix from the
 * requirements (task 12.1; Req 3.1, 3.7, 3.8). Spot-checks representative rows across scopes and
 * pins the cross-cutting invariants (Auditor read-only; Platform_Admin only platform-scoped rows).
 */
@DisplayName("PermissionMatrix")
class PermissionMatrixTest {

    @Test
    @DisplayName("platform-only operations are granted to Platform_Admin alone, at PLATFORM scope")
    void platformOnlyRows() {
        assertThat(PermissionMatrix.scopeFor(MembershipRole.PLATFORM_ADMIN, Permission.SOCIETY_LIFECYCLE_MANAGE))
                .isEqualTo(PermissionScope.PLATFORM);
        assertThat(PermissionMatrix.scopeFor(MembershipRole.PLATFORM_ADMIN, Permission.PLATFORM_METRICS_VIEW))
                .isEqualTo(PermissionScope.PLATFORM);
        // No other role may onboard/suspend societies or view platform metrics.
        for (MembershipRole role : MembershipRole.values()) {
            if (role != MembershipRole.PLATFORM_ADMIN) {
                assertThat(PermissionMatrix.isPermitted(role, Permission.SOCIETY_LIFECYCLE_MANAGE)).isFalse();
                assertThat(PermissionMatrix.isPermitted(role, Permission.PLATFORM_METRICS_VIEW)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("finance operations are limited to Society_Admin and Accountant at SOCIETY scope")
    void financeRows() {
        for (Permission p : new Permission[] {
                Permission.CHARGE_RULE_CONFIGURE, Permission.INVOICE_GENERATE,
                Permission.PAYMENT_RECORD, Permission.PAYMENT_ADJUST}) {
            assertThat(PermissionMatrix.scopeFor(MembershipRole.SOCIETY_ADMIN, p)).isEqualTo(PermissionScope.SOCIETY);
            assertThat(PermissionMatrix.scopeFor(MembershipRole.ACCOUNTANT, p)).isEqualTo(PermissionScope.SOCIETY);
            assertThat(PermissionMatrix.isPermitted(MembershipRole.OWNER, p)).isFalse();
            assertThat(PermissionMatrix.isPermitted(MembershipRole.SECURITY_GUARD, p)).isFalse();
        }
    }

    @Test
    @DisplayName("flat-scoped resident operations are granted at FLAT scope")
    void flatScopedRows() {
        assertThat(PermissionMatrix.scopeFor(MembershipRole.OWNER, Permission.VISIT_DECIDE)).isEqualTo(PermissionScope.FLAT);
        assertThat(PermissionMatrix.scopeFor(MembershipRole.TENANT_RESIDENT, Permission.VISIT_DECIDE)).isEqualTo(PermissionScope.FLAT);
        assertThat(PermissionMatrix.scopeFor(MembershipRole.OWNER, Permission.FAMILY_MEMBER_MANAGE)).isEqualTo(PermissionScope.FLAT);
        // Family members cannot manage other family members (dash in the matrix).
        assertThat(PermissionMatrix.isPermitted(MembershipRole.FAMILY_MEMBER, Permission.FAMILY_MEMBER_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("security guard may record visitors and view the log (SOCIETY), but not decide visits")
    void securityGuardRows() {
        assertThat(PermissionMatrix.scopeFor(MembershipRole.SECURITY_GUARD, Permission.VISITOR_RECORD)).isEqualTo(PermissionScope.SOCIETY);
        assertThat(PermissionMatrix.scopeFor(MembershipRole.SECURITY_GUARD, Permission.VISITOR_LOG_VIEW)).isEqualTo(PermissionScope.SOCIETY);
        assertThat(PermissionMatrix.isPermitted(MembershipRole.SECURITY_GUARD, Permission.VISIT_DECIDE)).isFalse();
    }

    @Test
    @DisplayName("Auditor holds only read/view permissions, all at ASSIGNED scope (Req 3.4)")
    void auditorIsReadOnly() {
        // The read operations the matrix grants the auditor, all at ASSIGNED scope.
        for (Permission p : new Permission[] {
                Permission.ANNOUNCEMENT_VIEW, Permission.DUES_VIEW, Permission.VISITOR_LOG_VIEW,
                Permission.BOOKING_VIEW, Permission.REPORT_GENERATE, Permission.AUDIT_TRAIL_VIEW}) {
            assertThat(PermissionMatrix.scopeFor(MembershipRole.AUDITOR, p))
                    .as("auditor %s must be ASSIGNED scope", p)
                    .isEqualTo(PermissionScope.ASSIGNED);
        }
        // The auditor must hold NO write/manage permission.
        for (Permission p : new Permission[] {
                Permission.SOCIETY_CONFIG_MANAGE, Permission.PROPERTY_STRUCTURE_MANAGE,
                Permission.RESIDENT_MANAGE, Permission.ROLE_ASSIGN, Permission.ANNOUNCEMENT_PUBLISH,
                Permission.CHARGE_RULE_CONFIGURE, Permission.INVOICE_GENERATE, Permission.PAYMENT_RECORD,
                Permission.PAYMENT_ADJUST, Permission.VISITOR_RECORD, Permission.VISIT_DECIDE,
                Permission.FACILITY_CONFIGURE, Permission.BOOKING_MANAGE, Permission.BOOKING_APPROVE,
                Permission.MFA_MANAGE, Permission.PRIVACY_REQUEST_HANDLE}) {
            assertThat(PermissionMatrix.isPermitted(MembershipRole.AUDITOR, p))
                    .as("auditor must NOT hold write permission %s (Req 3.4)", p)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Vendor holds only the scoped task view, at ASSIGNED scope")
    void vendorRows() {
        assertThat(PermissionMatrix.scopeFor(MembershipRole.VENDOR, Permission.VENDOR_TASK_VIEW)).isEqualTo(PermissionScope.ASSIGNED);
        long vendorGrants = java.util.Arrays.stream(Permission.values())
                .filter(p -> PermissionMatrix.isPermitted(MembershipRole.VENDOR, p))
                .count();
        assertThat(vendorGrants).as("vendor holds exactly one permission (the scoped task view)").isEqualTo(1);
    }

    @Test
    @DisplayName("an unlisted (role, permission) pair defaults to NONE")
    void defaultsToNone() {
        assertThat(PermissionMatrix.scopeFor(MembershipRole.MAINTENANCE_STAFF, Permission.INVOICE_GENERATE))
                .isEqualTo(PermissionScope.NONE);
        assertThat(PermissionMatrix.isPermitted(MembershipRole.FAMILY_MEMBER, Permission.PAYMENT_RECORD)).isFalse();
    }
}
