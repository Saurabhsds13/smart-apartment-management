package com.societix.authorization.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Marker for a domain object that belongs to exactly one Society (and optionally one Flat), so the
 * authorization layer can perform object-level (BOLA/IDOR) checks on it (task 12.3; Req 1.7, 18.3).
 *
 * <p>Any by-id access to a tenant-owned resource is checked with
 * {@link AuthorizationService#canAccessObject} before the object is returned or acted on, so a
 * resource belonging to another Society (or, for flat-scoped roles, another Flat) is treated as
 * not-found rather than disclosed (Req 1.7). Implemented by domain entities/DTOs that cross an
 * authorization boundary.
 */
public interface TenantOwned {

    /** The owning Society id of this object (never {@code null} for a tenant-owned object). */
    UUID owningSocietyId();

    /**
     * The owning Flat id, when the object is flat-scoped (e.g. a resident's own invoice or booking);
     * {@link Optional#empty()} for society-wide objects. Used to enforce FLAT-scope grants so a
     * flat-scoped actor cannot reach another flat's object (Req 18.3).
     */
    default Optional<UUID> owningFlatId() {
        return Optional.empty();
    }
}
