package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.societix.common.api.LoggingContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Focused unit tests for {@link TenantContextFilter} (Req 1.2, 1.3, 1.6, BR1).
 *
 * <p>The filter is driven directly with Spring's mock servlet request/response and a stub
 * {@link TenantResolver}, so the behaviour under test is the filter's own wiring, not the resolver
 * implementation. The tests verify that:
 * <ul>
 *   <li>a resolved society populates the context and the {@code societyId} MDC key;</li>
 *   <li>a platform-admin resolution sets the flag but puts no society id on the MDC;</li>
 *   <li>an anonymous/empty resolution leaves the context unresolved (Req 1.6);</li>
 *   <li>the society scope comes from the {@link Authentication}, never from the request (Req 1.2,
 *       BR1): a society id placed in a request header/param is ignored;</li>
 *   <li>the society MDC key is cleared after the request, even if the chain throws.</li>
 * </ul>
 */
@DisplayName("TenantContextFilter")
class TenantContextFilterTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("populates the society context and MDC from the authenticated principal")
    void populatesResolvedSociety() throws ServletException, IOException {
        UUID societyId = UUID.randomUUID();
        RequestScopedTenantContext context = new RequestScopedTenantContext();
        TenantContextFilter filter =
                new TenantContextFilter(context, auth -> Optional.of(AuthenticatedTenant.ofSociety(societyId)));
        authenticate();

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        AtomicReference<UUID> societyDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcDuringChain.set(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY));
            societyDuringChain.set(context.currentSocietyId());
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(societyDuringChain.get()).isEqualTo(societyId);
        assertThat(mdcDuringChain.get()).isEqualTo(societyId.toString());
        // Cleared after the request so a pooled thread cannot leak it into the next.
        assertThat(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("resolves platform-admin without putting a society id on the MDC")
    void populatesPlatformAdmin() throws ServletException, IOException {
        RequestScopedTenantContext context = new RequestScopedTenantContext();
        TenantContextFilter filter =
                new TenantContextFilter(context, auth -> Optional.of(AuthenticatedTenant.platformAdmin()));
        authenticate();

        AtomicBoolean platformAdminDuringChain = new AtomicBoolean();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            platformAdminDuringChain.set(context.isPlatformAdmin());
            mdcDuringChain.set(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY));
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(platformAdminDuringChain.get()).isTrue();
        assertThat(mdcDuringChain.get()).isNull();
    }

    @Test
    @DisplayName("leaves the context unresolved for an anonymous request (Req 1.6)")
    void leavesUnresolvedWhenAnonymous() throws ServletException, IOException {
        RequestScopedTenantContext context = new RequestScopedTenantContext();
        // Resolver returns empty, mirroring the default behaviour for anonymous/unauthenticated.
        TenantContextFilter filter = new TenantContextFilter(context, auth -> Optional.empty());

        AtomicBoolean resolvedDuringChain = new AtomicBoolean(true);
        FilterChain chain = (req, res) -> resolvedDuringChain.set(context.isResolved());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(resolvedDuringChain.get()).isFalse();
    }

    @Test
    @DisplayName("ignores a society id supplied in the request; uses only the authentication (Req 1.2, BR1)")
    void ignoresClientSuppliedSociety() throws ServletException, IOException {
        UUID authenticatedSociety = UUID.randomUUID();
        UUID clientSuppliedSociety = UUID.randomUUID();
        RequestScopedTenantContext context = new RequestScopedTenantContext();

        // A resolver that only ever reads the Authentication (never the request) — exactly the
        // contract TenantResolver enforces at the type level.
        TenantContextFilter filter = new TenantContextFilter(
                context, auth -> Optional.of(AuthenticatedTenant.ofSociety(authenticatedSociety)));
        authenticate();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Society-Id", clientSuppliedSociety.toString());
        request.setParameter("societyId", clientSuppliedSociety.toString());

        AtomicReference<UUID> societyDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> societyDuringChain.set(context.currentSocietyId());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(societyDuringChain.get()).isEqualTo(authenticatedSociety);
        assertThat(societyDuringChain.get()).isNotEqualTo(clientSuppliedSociety);
    }

    @Test
    @DisplayName("clears the society MDC key even when the downstream chain throws")
    void clearsMdcWhenChainThrows() {
        UUID societyId = UUID.randomUUID();
        RequestScopedTenantContext context = new RequestScopedTenantContext();
        TenantContextFilter filter =
                new TenantContextFilter(context, auth -> Optional.of(AuthenticatedTenant.ofSociety(societyId)));
        authenticate();

        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        } catch (ServletException | IOException expected) {
            // The filter must still clean up the MDC despite the failure.
        }

        assertThat(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("treats a Spring anonymous token as not authenticated via the default resolver")
    void defaultResolverTreatsAnonymousAsUnresolved() throws ServletException, IOException {
        RequestScopedTenantContext context = new RequestScopedTenantContext();
        TenantContextFilter filter = new TenantContextFilter(context, new DefaultTenantResolver());

        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        AtomicBoolean resolvedDuringChain = new AtomicBoolean(true);
        FilterChain chain = (req, res) -> resolvedDuringChain.set(context.isResolved());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(resolvedDuringChain.get()).isFalse();
    }

    /** Places a plain authenticated principal on the SecurityContext for the current thread. */
    private static void authenticate() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user", "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
