package com.societix.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for <strong>MFA enforcement for privileged roles</strong> (task 13.3;
 * design.md Correctness Property 12; Req 24.1, 24.2, 24.9).
 *
 * <p><strong>Feature: societix-platform, Property 12: MFA enforcement for privileged roles</strong>
 * - for all sets of roles, {@link MfaPolicy#isMfaRequiredFor} requires MFA if and only if the set
 * contains at least one privileged role (Platform_Admin, Society_Admin, Accountant, or
 * Facility_Manager). A resident-only set never requires MFA (it is offered, not enforced), and any
 * set that includes a privileged role always requires it, so a privileged sign-in cannot evade the
 * second factor (Req 24.9).
 */
@org.junit.jupiter.api.Tag("property")
@net.jqwik.api.Tag("property")
class MfaPolicyPropertyTest {

    private static final Set<String> PRIVILEGED =
            Set.of("PLATFORM_ADMIN", "SOCIETY_ADMIN", "ACCOUNTANT", "FACILITY_MANAGER");

    /**
     * For all role sets, MFA is required iff the set intersects the privileged set.
     *
     * <p><strong>Validates: Requirements 24.1, 24.2, 24.9</strong>
     */
    @Property(tries = 500)
    void mfaRequiredIffAnyPrivilegedRolePresent(@ForAll("roleSets") Set<String> roles) {
        boolean anyPrivileged = roles.stream().anyMatch(PRIVILEGED::contains);
        assertThat(MfaPolicy.isMfaRequiredFor(roles))
                .as("roles %s -> MFA required should be %s", roles, anyPrivileged)
                .isEqualTo(anyPrivileged);
    }

    @Property(tries = 200)
    void residentOnlySetsNeverRequireMfa(@ForAll("residentSets") Set<String> residentRoles) {
        assertThat(MfaPolicy.isMfaRequiredFor(residentRoles))
                .as("resident-only roles %s must not require MFA (offered, not enforced)", residentRoles)
                .isFalse();
    }

    @Provide
    Arbitrary<Set<String>> roleSets() {
        return Arbitraries.of(
                        "PLATFORM_ADMIN", "SOCIETY_ADMIN", "FACILITY_MANAGER", "ACCOUNTANT",
                        "SECURITY_GUARD", "OWNER", "TENANT_RESIDENT", "FAMILY_MEMBER",
                        "MAINTENANCE_STAFF", "VENDOR", "AUDITOR")
                .set().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Set<String>> residentSets() {
        return Arbitraries.of("OWNER", "TENANT_RESIDENT", "FAMILY_MEMBER")
                .set().ofMinSize(1).ofMaxSize(3);
    }
}
