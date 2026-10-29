package com.societix.authorization.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.societix.authorization.api.Actor;
import com.societix.authorization.api.AuthorizationException;
import com.societix.authorization.api.AuthorizationService;
import com.societix.authorization.api.Permission;
import com.societix.authorization.api.TenantOwned;
import com.societix.identity.api.MembershipRole;

/**
 * Unit tests for {@link AuthorizationServiceImpl} (tasks 12.2, 12.3; Req 3.2, 3.3, 3.4, 3.5, 1.7,
 * 18.3). Function-level checks and object-level (BOLA/IDOR) checks are driven directly with
 * server-derived {@link Actor}s and tenant-owned test objects; no Spring context or database.
 */
@DisplayName("AuthorizationServiceImpl")
class AuthorizationServiceImplTest {

    private final AuthorizationService service = new AuthorizationServiceImpl();

    private static final UUID SOCIETY_A = UUID.randomUUID();
    private static final UUID SOCIETY_B = UUID.randomUUID();
    private static final UUID FLAT_1 = UUID.randomUUID();
    private static final UUID FLAT_2 = UUID.randomUUID();

    private static Actor societyActor(MembershipRole role) {
        return new Actor(UUID.randomUUID(), Set.of(role), Optional.of(SOCIETY_A), Set.of(), false);
    }

    private static Actor flatActor(MembershipRole role, UUID flatId) {
        return new Actor(UUID.randomUUID(), Set.of(role), Optional.of(SOCIETY_A), Set.of(flatId), false);
    }

    private static Actor platformAdmin() {
        return new Actor(UUID.randomUUID(), Set.of(), Optional.empty(), Set.of(), true);
    }

    private record Obj(UUID society, UUID flat) implements TenantOwned {
        @Override public UUID owningSocietyId() { return society; }
        @Override public Optional<UUID> owningFlatId() { return Optional.ofNullable(flat); }
    }

    @Nested
    @DisplayName("function-level checks (Req 3.2, 3.3)")
    class FunctionLevel {

        @Test
        @DisplayName("a permitted role passes checkPermission")
        void permittedRolePasses() {
            assertThatCode(() ->
                    service.checkPermission(societyActor(MembershipRole.ACCOUNTANT), Permission.INVOICE_GENERATE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unpermitted role is denied with AuthorizationException")
        void unpermittedRoleDenied() {
            assertThatThrownBy(() ->
                    service.checkPermission(societyActor(MembershipRole.SECURITY_GUARD), Permission.INVOICE_GENERATE))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("a society-scoped grant requires a resolved society")
        void societyGrantRequiresSociety() {
            Actor noSociety = new Actor(UUID.randomUUID(), Set.of(MembershipRole.ACCOUNTANT),
                    Optional.empty(), Set.of(), false);
            assertThatThrownBy(() -> service.checkPermission(noSociety, Permission.INVOICE_GENERATE))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("a platform-only operation requires platform-admin; a society role is denied")
        void platformGrantRequiresPlatformAdmin() {
            assertThatCode(() -> service.checkPermission(platformAdmin(), Permission.SOCIETY_LIFECYCLE_MANAGE))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() ->
                    service.checkPermission(societyActor(MembershipRole.SOCIETY_ADMIN), Permission.SOCIETY_LIFECYCLE_MANAGE))
                    .isInstanceOf(AuthorizationException.class);
        }

        @Test
        @DisplayName("an auditor is denied any write permission (Req 3.4)")
        void auditorDeniedWrites() {
            assertThatThrownBy(() ->
                    service.checkPermission(societyActor(MembershipRole.AUDITOR), Permission.PAYMENT_RECORD))
                    .isInstanceOf(AuthorizationException.class);
            // ...but may generate reports (its ASSIGNED read grant).
            assertThatCode(() ->
                    service.checkPermission(societyActor(MembershipRole.AUDITOR), Permission.REPORT_GENERATE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a null actor is denied")
        void nullActorDenied() {
            assertThatThrownBy(() -> service.checkPermission(null, Permission.ANNOUNCEMENT_VIEW))
                    .isInstanceOf(AuthorizationException.class);
        }
    }

    @Nested
    @DisplayName("object-level BOLA/IDOR checks (Req 1.7, 18.3)")
    class ObjectLevel {

        @Test
        @DisplayName("a society-scoped actor may access an in-society object")
        void societyActorAccessesInSocietyObject() {
            assertThat(service.canAccessObject(
                    societyActor(MembershipRole.SOCIETY_ADMIN), Permission.DUES_VIEW, new Obj(SOCIETY_A, FLAT_1)))
                    .isTrue();
        }

        @Test
        @DisplayName("a cross-society object is not accessible (treated as not-found, Req 1.7)")
        void crossSocietyObjectDenied() {
            assertThat(service.canAccessObject(
                    societyActor(MembershipRole.SOCIETY_ADMIN), Permission.DUES_VIEW, new Obj(SOCIETY_B, FLAT_1)))
                    .isFalse();
        }

        @Test
        @DisplayName("a flat-scoped actor may access only its OWN flat's object (Req 18.3)")
        void flatActorAccessesOwnFlatOnly() {
            Actor owner = flatActor(MembershipRole.OWNER, FLAT_1);
            assertThat(service.canAccessObject(owner, Permission.DUES_VIEW, new Obj(SOCIETY_A, FLAT_1)))
                    .as("own flat is accessible").isTrue();
            assertThat(service.canAccessObject(owner, Permission.DUES_VIEW, new Obj(SOCIETY_A, FLAT_2)))
                    .as("another flat in the same society is NOT accessible (Req 18.3)").isFalse();
        }

        @Test
        @DisplayName("a platform-admin has no implicit object access on the ordinary path (Req 1.14)")
        void platformAdminNoImplicitObjectAccess() {
            assertThat(service.canAccessObject(platformAdmin(), Permission.DUES_VIEW, new Obj(SOCIETY_A, FLAT_1)))
                    .isFalse();
        }

        @Test
        @DisplayName("an object for a permission the actor lacks is not accessible")
        void unpermittedOperationDenied() {
            assertThat(service.canAccessObject(
                    societyActor(MembershipRole.SECURITY_GUARD), Permission.INVOICE_GENERATE, new Obj(SOCIETY_A, null)))
                    .isFalse();
        }

        @Test
        @DisplayName("null actor or object is not accessible")
        void nullsDenied() {
            assertThat(service.canAccessObject(null, Permission.DUES_VIEW, new Obj(SOCIETY_A, FLAT_1))).isFalse();
            assertThat(service.canAccessObject(societyActor(MembershipRole.SOCIETY_ADMIN), Permission.DUES_VIEW, null)).isFalse();
        }
    }
}
