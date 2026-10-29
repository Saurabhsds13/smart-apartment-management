package com.societix.authorization.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.societix.identity.api.MembershipRole;

/**
 * The authenticated actor's authorization-relevant identity, derived <strong>server-side</strong>
 * from the session/membership (never from client input, Req 1.2, BR1). Supplied to the
 * {@link AuthorizationService} for function- and object-level checks (tasks 12.2, 12.3).
 *
 * <p>An actor carries: its user id; the roles it holds in the current request's Tenant_Context; the
 * owning Society of that context (absent for a Platform_Admin acting at platform scope); and, for a
 * flat-scoped resident, the set of flat ids it is associated with (used for FLAT-scope object
 * checks). Platform_Admin is represented by {@link #platformAdmin} rather than a society role.
 *
 * @param userId        the authenticated user's id
 * @param roles         the roles held within the current society scope (empty for a pure platform admin)
 * @param societyId     the resolved owning Society, or empty for platform scope
 * @param flatIds       the flat ids the actor is associated with (for FLAT-scope checks); may be empty
 * @param platformAdmin whether the actor is acting as a Platform_Admin at platform scope
 */
public record Actor(
        UUID userId,
        Set<MembershipRole> roles,
        Optional<UUID> societyId,
        Set<UUID> flatIds,
        boolean platformAdmin) {

    public Actor {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        flatIds = flatIds == null ? Set.of() : Set.copyOf(flatIds);
        societyId = societyId == null ? Optional.empty() : societyId;
    }
}
