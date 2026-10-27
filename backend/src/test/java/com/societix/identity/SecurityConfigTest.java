package com.societix.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societix.common.internal.CorrelationIdFilter;
import com.societix.identity.internal.SecurityConfig;
import com.societix.tenancy.internal.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Behavioural tests for the Spring Security filter chain built by {@link SecurityConfig}
 * (task 10.1; design ADR-3; Req 2.1, 17.8, 17.9, A7).
 *
 * <p>This is a {@code @WebMvcTest} slice: it loads the web layer + the real
 * {@link SecurityConfig} (and Spring Boot's security auto-configuration) but <strong>no</strong>
 * JPA/datasource — so it runs with no Docker and no PostgreSQL. A tiny {@link ProtectedController}
 * stands in for a real protected API so the chain's authentication/CSRF/channel behaviour can be
 * asserted against concrete endpoints.
 *
 * <p>The suite runs under the default (non-prod) profile so channel security (requiresSecure) is
 * off and plain-HTTP MockMvc requests work; the prod-only channel rule is documented in
 * {@link SecurityConfig} and exercised separately by {@link SecurityConfigProdChannelTest}.
 *
 * <p>Covered:
 * <ul>
 *   <li>an unauthenticated request to a protected endpoint gets {@code 401} (not a redirect) —
 *       the SPA/JSON-API posture;</li>
 *   <li>CSRF is required on unsafe methods: a {@code POST} without a token is {@code 403}; the same
 *       {@code POST} with a valid token succeeds;</li>
 *   <li>safe methods are CSRF-exempt: an authenticated {@code GET} succeeds with no CSRF token;</li>
 *   <li>the CSRF token is delivered to the client as the SPA-readable {@code XSRF-TOKEN} cookie.</li>
 * </ul>
 */
@WebMvcTest(
        controllers = SecurityConfigTest.ProtectedController.class,
        // The slice auto-detects Filter beans; exclude the cross-cutting application filters so the
        // slice does not have to wire their (request-scoped tenancy) collaborators. Their ordering
        // relative to the security chain is asserted by their own unit tests and documented in
        // SecurityConfig; here we focus purely on the security chain's behaviour.
        excludeFilters = {
            @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
            @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CorrelationIdFilter.class)
        })
@Import(SecurityConfig.class)
@DisplayName("SecurityConfig filter chain (non-prod)")
class SecurityConfigTest {

    /** Minimal protected API used only to exercise the security chain. */
    @RestController
    static class ProtectedController {

        @GetMapping("/api/v1/protected")
        String read() {
            return "ok-get";
        }

        @PostMapping("/api/v1/protected")
        String write() {
            return "ok-post";
        }

        /**
         * Stands in for the real {@code /api/v1/auth/csrf} endpoint the SPA calls to obtain its
         * token. Reading the {@link CsrfToken} forces the {@code CookieCsrfTokenRepository} to
         * materialise and persist the {@code XSRF-TOKEN} cookie, which is exactly how the SPA gets
         * the value it later echoes in {@code X-XSRF-TOKEN}.
         */
        @GetMapping("/api/v1/auth/csrf")
        String csrf(CsrfToken token) {
            return token.getToken();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("unauthenticated request to a protected endpoint returns 401 (not a redirect)")
        void unauthenticatedProtectedReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/protected")).andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        @DisplayName("authenticated GET to a protected endpoint succeeds (CSRF-exempt safe method)")
        void authenticatedGetSucceedsWithoutCsrf() throws Exception {
            mockMvc.perform(get("/api/v1/protected")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CSRF on unsafe methods")
    class Csrf {

        @Test
        @WithMockUser
        @DisplayName("POST without a CSRF token is rejected with 403")
        void postWithoutCsrfIsForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/protected")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        @DisplayName("POST with a valid CSRF token succeeds")
        void postWithCsrfSucceeds() throws Exception {
            mockMvc.perform(post("/api/v1/protected")
                            .with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("the CSRF token is delivered to the SPA as the non-HttpOnly XSRF-TOKEN cookie")
        void csrfTokenDeliveredAsXsrfCookie() throws Exception {
            // Hitting the public CSRF endpoint materialises the token; the repository then sets the
            // XSRF-TOKEN cookie the SPA (lib/csrf.ts) reads. It must be readable by JS (not
            // HttpOnly) so the SPA can echo it back on unsafe methods.
            mockMvc.perform(get("/api/v1/auth/csrf"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("XSRF-TOKEN"))
                    .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
        }
    }

    @Nested
    @DisplayName("public endpoints (non-prod)")
    class PublicEndpoints {

        @Test
        @DisplayName("actuator health is permitted without authentication (not blocked by security)")
        void healthIsPublic() throws Exception {
            // The actuator endpoint is not wired in this web slice, so we cannot assert its 200;
            // what this test proves is that the SECURITY chain permits the request rather than
            // rejecting it — i.e. the response is neither 401 (unauthenticated) nor 403 (forbidden).
            int statusCode = mockMvc.perform(get("/actuator/health"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            org.assertj.core.api.Assertions.assertThat(statusCode)
                    .as("actuator/health must pass the security chain (not 401/403)")
                    .isNotIn(401, 403);
        }
    }
}
