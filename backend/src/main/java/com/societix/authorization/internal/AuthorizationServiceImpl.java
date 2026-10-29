package com.societix.authorization.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.societix.authorization.api.Actor;
import com.societix.authorization.api.AuthorizationException;
import com.societix.authorization.api.AuthorizationService;
import com.societix.authorization.api.Permission;
import com.societix.authorization.api.PermissionMatrix;
import com.societix.authorization.api.PermissionScope;
import com.societix.authorization.api.TenantOwned;
import com.societix.identity.api.MembershipRole;

/**
 * Default {@link AuthorizationService} enforcing the authoritative {@link PermissionMatrix} at the
 * function and object level (tasks 12.2, 12.3; Req 3.2, 3.3, 3.4, 3.5, 1.7, 18.3).
 *
 * <p>The decision is driven entirely by the matrix and the server-derived {@link Actor}; no call
 * site encodes its own role list, and nothing here reads client input. A denial throws
 * {@link AuthorizationException} (mapped to 403) and the caller makes no partial change (Req 3.3);
 * cross-tenant/unowned object access is reported as not-found via {@code canAccessObject} returning
 * {@code false} so existence is never disclosed (Req 1.7).
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    @Override
    public void checkPermission(Actor actor, Permission permission) {
        if (actor == null || !hasGrant(actor, permission)) {
            throw new AuthorizationException();
        }
    }

    @Override
    public boolean canAccessObject(Actor actor, Permission permission, TenantOwned object) {
        if (actor == null || object == null) {
            return false;
        }
        PermissionScope best = bestScope(actor, permission);
        if (best == PermissionScope.NONE) {
            return false; // no role grants this at all -> not-found (Req 1.7)
        }

        // Platform-admin acts only through the audited platform path, never via the ordinary
        // object-level resident/society data path (Req 1.14). So a platform-only grant here denies.
        if (best == PermissionScope.PLATFORM) {
            return false;
        }

        // Every non-platform grant requires the object to belong to the actor's resolved Society.
        Optional<UUID> actorSociety = actor.societyId();
        if (actorSociety.isEmpty() || !actorSociety.get().equals(object.owningSocietyId())) {
            return false; // cross-tenant -> not-found (Req 1.7)
        }

        // If the actor's grant for this permission is FLAT-scoped ONLY (no society/assigned grant),
        // the object's owning flat must be one of the actor's flats (Req 18.3).
        if (best == PermissionScope.FLAT) {
            Optional<UUID> objectFlat = object.owningFlatId();
            return objectFlat.isPresent() && actor.flatIds().contains(objectFlat.get());
        }

        // SOCIETY or ASSIGNED grant and same society: allowed (ASSIGNED sub-scoping, e.g. an
        // auditor's or vendor's assigned subset, is enforced by the specific service that issued
        // the grant; at the object level, same-society membership is the boundary).
        return true;
    }

    /** True if any role the actor holds (or platform-admin) grants the permission at a usable scope. */
    private static boolean hasGrant(Actor actor, Permission permission) {
        PermissionScope best = bestScope(actor, permission);
        return switch (best) {
            case PLATFORM -> actor.platformAdmin();
            case SOCIETY, FLAT, ASSIGNED -> actor.societyId().isPresent();
            case NONE -> false;
        };
    }

    /**
     * Returns the most permissive scope any of the actor's roles grants for the permission.
     * A Platform_Admin actor is treated as holding the PLATFORM_ADMIN role for matrix lookup.
     */
    private static PermissionScope bestScope(Actor actor, Permission permission) {
        PermissionScope best = PermissionScope.NONE;
        if (actor.platformAdmin()) {
            best = morePermissive(best,
                    PermissionMatrix.scopeFor(MembershipRole.PLATFORM_ADMIN, permission));
        }
        for (MembershipRole role : actor.roles()) {
            best = morePermissive(best, PermissionMatrix.scopeFor(role, permission));
        }
        return best;
    }

    /**
     * Picks the broader of two scopes. Ordering from broadest to narrowest:
     * PLATFORM &gt; SOCIETY &gt; ASSIGNED &gt; FLAT &gt; NONE. This means an actor that holds both a
     * society-wide role and a flat-scoped role for the same permission gets the society-wide reach
     * (the broader grant), matching the matrix's intent that the stronger role wins.
     */
    private static PermissionScope morePermissive(PermissionScope a, PermissionScope b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(PermissionScope scope) {
        return switch (scope) {
            case PLATFORM -> 4;
            case SOCIETY -> 3;
            case ASSIGNED -> 2;
            case FLAT -> 1;
            case NONE -> 0;
        };
    }
}
