package com.societix.tenancy.internal;

import com.societix.common.api.LoggingContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates the request-scoped {@link RequestScopedTenantContext} from the
 * authenticated session, <strong>after</strong> authentication has run (design.md "TenantContext
 * and Tenant Scoping", ADR-2; Req 1.2, 1.3, BR1).
 *
 * <h2>Ordering — runs after authentication, inside the correlation-id window</h2>
 * <ul>
 *   <li>It runs <em>after</em> the Spring Security filter chain so the
 *       {@code SecurityContextHolder} already holds the request's {@link Authentication}. Spring
 *       Security registers at {@link SecurityProperties#DEFAULT_FILTER_ORDER} ({@code -100}); this
 *       filter uses a strictly greater order so it executes once authentication/anonymous
 *       resolution is complete. (Spring Security itself is wired in task 10.1; until then the
 *       {@code SecurityContextHolder} is simply empty/anonymous and the context stays unresolved.)</li>
 *   <li>It runs <em>after</em> the {@link com.societix.common.internal.CorrelationIdFilter} (which
 *       sits at {@link Ordered#HIGHEST_PRECEDENCE}), so the correlation id is already on the MDC and
 *       this filter can add the {@code societyId} key within that same window. The correlation-id
 *       filter clears both MDC keys in its {@code finally} block, so this filter does not itself
 *       need to remove the society id — but it does, defensively, so a {@code null}-session code
 *       path can never leave a stale society id behind.</li>
 * </ul>
 *
 * <h2>Server-derived only (Req 1.2, 1.3, BR1)</h2>
 * The society is resolved solely from the {@link Authentication} via the {@link TenantResolver}; the
 * {@link HttpServletRequest} is passed to {@code doFilterInternal} only to continue the chain and is
 * never inspected for a society identifier. A client therefore cannot set, widen, or override its
 * own tenant scope through any header, parameter, path, or body.
 *
 * <h2>Resolution outcome</h2>
 * <ul>
 *   <li>authenticated member of one Society → {@link RequestScopedTenantContext#resolveSociety} and
 *       the society id is placed on the MDC via {@link LoggingContext#putSocietyId(String)};</li>
 *   <li>authenticated Platform_Admin → {@link RequestScopedTenantContext#resolvePlatformAdmin} (no
 *       society id on the MDC, since platform scope has no single society);</li>
 *   <li>anonymous / no derivable society → the context is left unresolved, so tenant-owned
 *       operations abort and return zero rows (Req 1.6).</li>
 * </ul>
 */
@Component
@Order(TenantContextFilter.ORDER)
public class TenantContextFilter extends OncePerRequestFilter {

    /**
     * Filter order: strictly after Spring Security's filter chain
     * ({@link SecurityProperties#DEFAULT_FILTER_ORDER}) so the authenticated principal is available,
     * and well after the correlation-id filter at {@link Ordered#HIGHEST_PRECEDENCE}.
     */
    static final int ORDER = SecurityProperties.DEFAULT_FILTER_ORDER + 10;

    private final RequestScopedTenantContext tenantContext;
    private final TenantResolver tenantResolver;

    /**
     * @param tenantContext the request-scoped context to populate; injected as a scoped proxy so the
     *     per-request instance is targeted even though this filter is a singleton
     * @param tenantResolver derives the tenant scope from the authenticated principal
     */
    public TenantContextFilter(
            RequestScopedTenantContext tenantContext, TenantResolver tenantResolver) {
        this.tenantContext = tenantContext;
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            populateFromAuthentication();
            filterChain.doFilter(request, response);
        } finally {
            // Belt-and-braces: the correlation-id filter also clears this, but clearing here means a
            // code path where that outer filter is absent (e.g. a focused test) still cannot leak a
            // society id onto a pooled thread.
            LoggingContext.removeSocietyId();
        }
    }

    /**
     * Reads the authenticated principal from the {@code SecurityContextHolder} and populates the
     * request-scoped context. The request object is intentionally not consulted (Req 1.2, BR1).
     */
    private void populateFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<AuthenticatedTenant> resolved = tenantResolver.resolve(authentication);
        if (resolved.isEmpty()) {
            // Anonymous or no derivable society: leave the context unresolved (Req 1.6).
            return;
        }
        AuthenticatedTenant tenant = resolved.get();
        if (tenant.isPlatformAdmin()) {
            tenantContext.resolvePlatformAdmin();
            return;
        }
        tenant.societyId().ifPresent(societyId -> {
            tenantContext.resolveSociety(societyId);
            // Tag every subsequent log line on this request with the society id (Req 21.1).
            LoggingContext.putSocietyId(societyId.toString());
        });
    }
}
