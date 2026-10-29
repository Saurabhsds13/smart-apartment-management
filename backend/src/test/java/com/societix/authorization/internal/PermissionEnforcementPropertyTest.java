package com.societix.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.societix.authorization.api.Actor;
import com.societix.authorization.api.AuthorizationException;
import com.societix.authorization.api.AuthorizationService;
import com.societix.authorization.api.Permission;
import com.societix.authorization.api.PermissionMatrix;
import com.societix.authorization.api.PermissionScope;
import com.societix.authorization.api.TenantOwned;
import com.societix.identity.api.MembershipRole;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for <strong>permission-matrix enforcement</strong> (task 12.4; design.md
 * Correctness Property 10; Req 3.2, 3.3, 3.8).
 *
 * <p><strong>Feature: societix-platform, Property 10: Permission-matrix enforcement</strong> ? for
 * all (role, permission) pairs, the {@link AuthorizationService} authorizes a function-level check
 * if and only if the authoritative {@link PermissionMatrix} grants that role the permission at a
 * scope the actor's resolved context can satisfy; a denied check throws and performs no partial
 * state change (there is nothing to roll back because the check precedes any work). The object-level
 * check never grants access to a cross-society object regardless of role.
 *
 * <p>Driven against the real {@link AuthorizationServiceImpl} and the real matrix with no database,
 * so it runs fast and locally.
 */
@org.junit.jupiter.api.Tag("property")
@net.jqwik.api.Tag("property")
class PermissionEnforcementPropertyTest {

    private final AuthorizationService service = new AuthorizationServiceImpl();

    private static final UUID SOCIETY = UUID.randomUUID();
    private static final UUID OTHER_SOCIETY = UUID.randomUUID();

    /**
     * For all (role, permission): a society-resolved actor holding exactly that role is authorized
     * by checkPermission IFF the matrix grants the role a society-usable scope (SOCIETY/FLAT/ASSIGNED);
     * a PLATFORM-only grant or a NONE is denied for a society actor.
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.8</strong>
     */
    @Property(tries = 500)
    void checkPermissionAuthorizesIffMatrixPermitsForSocietyActor(
            @ForAll("roles") MembershipRole role, @ForAll("permissions") Permission permission) {
        Actor actor = new Actor(UUID.randomUUID(), Set.of(role), Optional.of(SOCIETY), Set.of(), false);
        PermissionScope granted = PermissionMatrix.scopeFor(role, permission);
        boolean societyUsable = granted == PermissionScope.SOCIETY
                || granted == PermissionScope.FLAT
                || granted == PermissionScope.ASSIGNED;

        boolean allowed;
        try {
            service.checkPermission(actor, permission);
            allowed = true;
        } catch (AuthorizationException denied) {
            allowed = false;
        }

        assertThat(allowed)
                .as("role %s, permission %s: granted scope %s -> societyActor allowed should be %s",
                        role, permission, granted, societyUsable)
                .isEqualTo(societyUsable);
    }

    /**
     * For all (role, permission): a platform-admin actor is authorized IFF the matrix grants the
     * PLATFORM_ADMIN role a PLATFORM scope for that permission.
     *
     * <p><strong>Validates: Requirements 3.2, 3.5, 3.8</strong>
     */
    @Property(tries = 200)
    void platformAdminAuthorizedIffPlatformGrant(@ForAll("permissions") Permission permission) {
        Actor admin = new Actor(UUID.randomUUID(), Set.of(), Optional.empty(), Set.of(), true);
        boolean platformGrant =
                PermissionMatrix.scopeFor(MembershipRole.PLATFORM_ADMIN, permission) == PermissionScope.PLATFORM;

        boolean allowed;
        try {
            service.checkPermission(admin, permission);
            allowed = true;
        } catch (AuthorizationException denied) {
            allowed = false;
        }
        assertThat(allowed).isEqualTo(platformGrant);
    }

    /**
     * For all (role, permission): a cross-society object is NEVER accessible, whatever the role ?
     * object-level isolation holds across the whole matrix (Req 1.7).
     *
     * <p><strong>Validates: Requirements 1.7, 3.8</strong>
     */
    @Property(tries = 500)
    void crossSocietyObjectNeverAccessible(
            @ForAll("roles") MembershipRole role, @ForAll("permissions") Permission permission) {
        Actor actor = new Actor(UUID.randomUUID(), Set.of(role), Optional.of(SOCIETY), Set.of(), false);
        TenantOwned foreign = new TenantOwned() {
            @Override public UUID owningSocietyId() { return OTHER_SOCIETY; }
        };
        assertThat(service.canAccessObject(actor, permission, foreign))
                .as("role %s must never reach a cross-society object", role)
                .isFalse();
    }

    @Provide
    Arbitrary<MembershipRole> roles() {
        return Arbitraries.of(MembershipRole.values());
    }

    @Provide
    Arbitrary<Permission> permissions() {
        return Arbitraries.of(Permission.values());
    }
}
