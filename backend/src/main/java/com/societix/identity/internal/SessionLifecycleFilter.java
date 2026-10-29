package com.societix.identity.internal;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.societix.identity.api.SessionLifecyclePolicy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Enforces the role-aware idle timeout and the absolute session-lifetime cap that the servlet
 * container's single global timeout cannot express (task 10.2; design ADR-3; Req 2.10, 2.11, 2.12).
 *
 * <p>On each request that already has an authenticated session, this filter computes how long the
 * session has been idle (now - lastAccessedTime) and how old it is (now - creationTime) and asks
 * {@link SessionLifecyclePolicy} whether either limit is exceeded for the actor's roles. If so it
 * invalidates the session, clears the security context, and returns {@code 401} so the client must
 * re-authenticate (Req 2.10-2.12). Anonymous/sessionless requests pass straight through.
 *
 * <h2>Ordering</h2>
 * Runs just after the Spring Security chain (like {@link com.societix.tenancy.internal.TenantContextFilter})
 * so the {@link Authentication} and its authorities are resolved, but before application handlers,
 * so an over-idle or over-age session is terminated before it can perform any operation.
 */
@Component
@Order(SessionLifecycleFilter.ORDER)
public class SessionLifecycleFilter extends OncePerRequestFilter {

    /** After the security chain; same precedence family as the tenant-context filter. */
    static final int ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 5;

    /** Authority prefix Spring Security adds to role names (e.g. ROLE_SOCIETY_ADMIN). */
    private static final String ROLE_PREFIX = "ROLE_";

    private final Clock clock;

    public SessionLifecycleFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (session != null && isAuthenticated(authentication)) {
            Instant now = clock.instant();
            Duration idle = Duration.between(Instant.ofEpochMilli(session.getLastAccessedTime()), now);
            Duration age = Duration.between(Instant.ofEpochMilli(session.getCreationTime()), now);
            Set<String> roles = rolesOf(authentication);

            if (SessionLifecyclePolicy.isExpired(idle, age, roles)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(HttpStatus.UNAUTHORIZED.value());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    /** Extracts bare role names (stripping the {@code ROLE_} authority prefix) from the authentication. */
    private static Set<String> rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
