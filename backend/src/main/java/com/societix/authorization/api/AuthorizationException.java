package com.societix.authorization.api;

import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when an authenticated actor is denied a protected operation by the
 * {@link AuthorizationService} (task 12.2; Req 3.3, 17.10).
 *
 * <p>It extends Spring Security's {@link AccessDeniedException} so the existing global exception
 * handler maps it to HTTP {@code 403 Forbidden} with the platform's generic problem+json envelope,
 * without the {@code common} module needing to depend on this {@code authorization} module (which
 * would invert the allowed dependency direction). It carries a fixed, generic message only ? never
 * the actor's roles, the required permission, or any internal identifier (Req 17.10). Object-level
 * cross-tenant denials are reported as not-found instead (see
 * {@link AuthorizationService#canAccessObject}) so existence is not disclosed (Req 1.7).
 */
public class AuthorizationException extends AccessDeniedException {

    public AuthorizationException() {
        super("You do not have permission to perform this operation.");
    }
}
