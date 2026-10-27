package com.societix.identity.internal;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Spring Security configuration for the same-origin SPA — server-side sessions, secure session
 * cookie, synchronizer-token CSRF, channel (transport) security, and the baseline authorization
 * rules (task 10.1; design.md ADR-3; Req 2.1, 17.8, 17.9, A7).
 *
 * <h2>Why server-side sessions (ADR-3)</h2>
 * The design's chosen authentication approach for this same-origin SPA is Spring Security
 * <strong>server-side sessions</strong> (not JWT): immediate revocation (Req 2.15), precise
 * idle/absolute lifetime control (Req 2.10–2.12, task 10.2), and keeping the credential out of
 * JavaScript's reach via an {@code HttpOnly} session cookie. CSRF protection is the bounded,
 * well-understood cost of cookie-based auth and is enabled below.
 *
 * <h2>What this task owns vs. defers</h2>
 * This class establishes the {@link SecurityFilterChain}, the session/cookie posture, CSRF, channel
 * security, and sensible baseline authorization. It deliberately does <em>not</em> implement login
 * throttling/lockout or session-lifetime timeouts (task 10.2), password reset/verification
 * (task 10.3), the full Permission Matrix method security (task 12.x), or MFA (task 13.x). A real
 * {@code UserDetailsService}/authentication provider also lands with those tasks; here the chain
 * simply authenticates nobody by default, so protected endpoints return {@code 401} until login is
 * wired.
 *
 * <h2>Filter ordering (coordination with the cross-cutting filters)</h2>
 * <ul>
 *   <li>{@link com.societix.common.internal.CorrelationIdFilter} runs at
 *       {@code HIGHEST_PRECEDENCE}, before this security chain, so auth/authz failures are already
 *       tagged with a correlation id.</li>
 *   <li>This security chain is registered by Spring Boot at
 *       {@link org.springframework.boot.autoconfigure.security.SecurityProperties#DEFAULT_FILTER_ORDER}
 *       ({@code -100}).</li>
 *   <li>{@link com.societix.tenancy.internal.TenantContextFilter} is ordered at
 *       {@code DEFAULT_FILTER_ORDER + 10}, i.e. strictly <em>after</em> this chain, so it populates
 *       the tenant context from the already-authenticated principal (Req 1.2, BR1).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The cookie name the SPA reads the CSRF token from. Kept in lockstep with the frontend
     * {@code lib/csrf.ts} ({@code CSRF_COOKIE_NAME = 'XSRF-TOKEN'}).
     */
    static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    /**
     * The request header the SPA echoes the CSRF token back in on unsafe methods. Matches the
     * frontend {@code lib/csrf.ts} ({@code CSRF_HEADER_NAME = 'X-XSRF-TOKEN'}).
     */
    static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /** Endpoints that are reachable without authentication (see {@link #securityFilterChain}). */
    static final String[] PUBLIC_ENDPOINTS = {
        // Kubernetes-style probes must be reachable by the orchestrator without a session.
        "/actuator/health",
        "/actuator/health/**",
        // Authentication entry points: a client must be able to log in and obtain a CSRF token
        // before it holds a session. (The login endpoint itself is wired in task 10.2/10.3.)
        "/api/v1/auth/login",
        "/api/v1/auth/csrf"
    };

    /**
     * Non-prod-only endpoints (OpenAPI docs + Swagger UI). In prod these require authentication so
     * the API surface is not published anonymously (design "REST Conventions"; task 2.4).
     */
    static final String[] NON_PROD_DOC_ENDPOINTS = {
        "/api/v1/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };

    private final Environment environment;
    private final ServerProperties serverProperties;

    SecurityConfig(Environment environment, ServerProperties serverProperties) {
        this.environment = environment;
        this.serverProperties = serverProperties;
    }

    /**
     * Builds the single application security filter chain.
     *
     * <p>Behaviour summary:
     * <ul>
     *   <li><strong>Sessions:</strong> {@link SessionCreationPolicy#IF_REQUIRED} — a server-side
     *       {@code HttpSession} is created when authentication needs one. The session cookie's
     *       {@code Secure}/{@code HttpOnly}/{@code SameSite} attributes are configured via
     *       {@code server.servlet.session.cookie.*} in {@code application.yml}; see
     *       {@link #sessionCookieHttpOnly()} etc. for the asserted defaults.</li>
     *   <li><strong>CSRF:</strong> enabled via {@link CookieCsrfTokenRepository} with the cookie
     *       readable by JS ({@code HttpOnly=false}) so the SPA can echo it; required on unsafe
     *       methods (POST/PUT/PATCH/DELETE), exempt on safe methods (GET/HEAD/OPTIONS/TRACE), which
     *       Spring Security's CSRF filter enforces by default.</li>
     *   <li><strong>Channel security (Req 17.9):</strong> in the {@code prod} profile every request
     *       must arrive over HTTPS ({@code requiresChannel().requiresSecure()}); combined with
     *       {@link #forwardedHeaderFilter()} this honours {@code X-Forwarded-Proto} from the TLS
     *       -terminating reverse proxy. Non-prod profiles keep plain HTTP so local/test work.</li>
     *   <li><strong>Authorization baseline:</strong> public endpoints are permitted; docs are
     *       permitted only in non-prod; everything else requires authentication. The richer
     *       Permission Matrix is task 12.x.</li>
     *   <li><strong>Unauthenticated API responses:</strong> a {@code 401} is returned (not a login
     *       redirect) because the client is an SPA talking to a JSON API.</li>
     * </ul>
     *
     * @param http the Spring Security builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean prod = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"));

        // CSRF: deliver the token to the SPA as a non-HttpOnly cookie so JS can read it and echo it
        // back in the X-XSRF-TOKEN header. withHttpOnlyFalse() is required for the SPA to read it.
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName(CSRF_COOKIE_NAME);
        csrfTokenRepository.setHeaderName(CSRF_HEADER_NAME);
        // Secure the CSRF cookie in prod (over HTTPS); leave it usable over plain HTTP elsewhere.
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.secure(prod).sameSite("Lax"));

        // Keep the raw token (no BREACH-style deferred loading quirks for a classic SPA) so the
        // value in the cookie is the value the server validates against the echoed header.
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                // Stateless-URL concerns don't apply: we use server-side sessions created on demand.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                    if (prod) {
                        // In prod, docs + the Prometheus/metrics scrape endpoints require
                        // authentication (task 2.3 promised this gating; task 10.1 enforces it).
                        auth.requestMatchers(NON_PROD_DOC_ENDPOINTS).authenticated();
                        auth.requestMatchers("/actuator/**").authenticated();
                    } else {
                        auth.requestMatchers(NON_PROD_DOC_ENDPOINTS).permitAll();
                        // Non-prod: allow operators/devs to scrape metrics and read actuator freely.
                        auth.requestMatchers("/actuator/**").permitAll();
                    }
                    // Default deny: every other endpoint requires an authenticated session.
                    auth.anyRequest().authenticated();
                })
                // SPA talking to a JSON API: answer unauthenticated requests with 401, not a
                // login-page redirect.
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // No HTTP Basic / form-login page is exposed; the SPA authenticates through the
                // dedicated login endpoint wired in task 10.2/10.3.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(Customizer.withDefaults());

        if (prod) {
            // Req 17.9: reject requests that did not arrive over an encrypted transport. TLS is
            // terminated at the reverse proxy; forwardedHeaderFilter() makes Spring honour the
            // proxy's X-Forwarded-Proto so this check passes for genuine HTTPS traffic and rejects
            // plain HTTP at the app edge.
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        return http.build();
    }

    /**
     * Registers a {@link ForwardedHeaderFilter} so the app honours {@code X-Forwarded-*} headers
     * from the TLS-terminating reverse proxy (design "Deployment Architecture"; Req 17.8, 17.9).
     *
     * <p>Without this, requests proxied as HTTP internally would look insecure to
     * {@code requiresChannel().requiresSecure()} even when the client connection was HTTPS. This
     * complements {@code server.forward-headers-strategy=framework} in {@code application.yml}.
     *
     * @return the forwarded-header filter bean
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }

    // ------------------------------------------------------------------
    // Session-cookie attribute accessors — these surface the configured posture (set via
    // server.servlet.session.cookie.* in application.yml) so tests and operators can assert it.
    // ------------------------------------------------------------------

    /** @return the configured session-cookie {@code HttpOnly} flag (expected {@code true}). */
    boolean sessionCookieHttpOnly() {
        Boolean httpOnly = serverProperties.getServlet().getSession().getCookie().getHttpOnly();
        return Boolean.TRUE.equals(httpOnly);
    }

    /** @return the configured session-cookie {@code SameSite} attribute (expected {@code Lax}). */
    String sessionCookieSameSite() {
        var sameSite = serverProperties.getServlet().getSession().getCookie().getSameSite();
        return sameSite == null ? null : sameSite.attributeValue();
    }

    /**
     * @return the configured session-cookie {@code Secure} flag. {@code null} means "secure when the
     *     request is secure" (the servlet default), which is the correct posture behind a
     *     TLS-terminating proxy; prod pins it to {@code true} in {@code application.yml}.
     */
    Boolean sessionCookieSecure() {
        return serverProperties.getServlet().getSession().getCookie().getSecure();
    }
}
