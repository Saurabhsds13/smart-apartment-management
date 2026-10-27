package com.societix.tenancy.internal;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Default {@link TenantResolver} used until the authentication wiring (task 10.1) and the
 * role/membership model (task 12.x) supply a principal that carries the actor's owning Society.
 *
 * <h2>Current behaviour</h2>
 * <ul>
 *   <li><strong>Anonymous / unauthenticated</strong> — a {@code null} authentication, an
 *       unauthenticated token, or an {@link AnonymousAuthenticationToken} resolves to
 *       {@link Optional#empty()}. The {@link TenantContext} is left unresolved, so tenant-owned
 *       operations abort and return zero rows (Req 1.6).</li>
 *   <li><strong>Authenticated</strong> — there is no society-bearing principal yet, so this
 *       resolver returns {@link Optional#empty()} for now. The {@link #resolveAuthenticated}
 *       seam below is the intended place for task 10.1/12.x to map the authenticated principal to
 *       its {@link AuthenticatedTenant} (one owning Society, or platform-admin).</li>
 * </ul>
 *
 * <p>This component can be replaced wholesale by a later task via a higher-priority bean, or
 * extended by filling in {@link #resolveAuthenticated(Authentication)}. Either way the filter and
 * the rest of the tenancy module are unaffected because they depend only on {@link TenantResolver}.
 *
 * <p>Crucially, resolution never consults the HTTP request — only the {@link Authentication} — so a
 * client can never supply or override its own Society (Req 1.2, 1.3, BR1).
 */
@Component
class DefaultTenantResolver implements TenantResolver {

    @Override
    public Optional<AuthenticatedTenant> resolve(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return Optional.empty();
        }
        return resolveAuthenticated(authentication);
    }

    /**
     * Returns {@code true} only for a genuinely authenticated principal. A {@code null} token, a
     * token flagged not-authenticated, or Spring Security's anonymous token are all treated as
     * "no authenticated principal" and therefore leave the tenant context unresolved.
     */
    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * Maps an already-authenticated principal to its tenant scope.
     *
     * <p><strong>Extension point (task 10.1 / 12.x).</strong> The authenticated principal does not
     * yet carry a Society (session/MFA/membership wiring is implemented later), so this returns
     * {@link Optional#empty()} today. When the principal model lands, map it here: return
     * {@link AuthenticatedTenant#ofSociety(java.util.UUID)} for a society-scoped member, or
     * {@link AuthenticatedTenant#platformAdmin()} for a Platform_Admin. The value MUST come from the
     * authenticated principal, never from the request (Req 1.2, BR1).
     *
     * @param authentication a non-null, authenticated, non-anonymous authentication
     * @return the resolved tenant scope, or {@link Optional#empty()} if no Society can yet be derived
     */
    protected Optional<AuthenticatedTenant> resolveAuthenticated(Authentication authentication) {
        return Optional.empty();
    }
}
