package com.societix.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.server.Session;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the server-side session cookie is configured with the hardened attributes required by
 * the design (task 10.1; design ADR-3; Req 2.1).
 *
 * <p>The attributes are set declaratively via {@code server.servlet.session.cookie.*} in
 * {@code application.yml} and bound onto {@link ServerProperties}. This test loads only the
 * configuration-properties binding (no web server, no JPA, no datasource, no Docker) and asserts
 * the bound values, so a regression that weakens the cookie posture fails the build:
 * <ul>
 *   <li>{@code HttpOnly = true} — the session cookie is never readable by JavaScript;</li>
 *   <li>{@code SameSite = Lax} — blocks the cookie on cross-site sub-requests;</li>
 *   <li>a stable cookie name is set.</li>
 * </ul>
 * The {@code Secure} flag is profile-dependent (pinned {@code true} only in prod, so local/test over
 * plain HTTP still works); {@link #prodPinsSecureCookie()} covers the prod value.
 */
class SessionCookieConfigTest {

    /**
     * Minimal boot context that binds {@link ServerProperties} from {@code application.yml} without
     * standing up the web server or touching a database.
     */
    @SpringBootTest(classes = SessionCookieConfigTest.PropsOnlyConfig.class)
    @ActiveProfiles("test")
    @DisplayName("session cookie attributes (base/non-prod)")
    static class NonProd {

        @Autowired
        private ServerProperties serverProperties;

        @Test
        @DisplayName("session cookie is HttpOnly, SameSite=Lax, and named")
        void cookieIsHardened() {
            Session.Cookie cookie = serverProperties.getServlet().getSession().getCookie();
            assertThat(cookie.getHttpOnly()).as("HttpOnly").isTrue();
            assertThat(cookie.getSameSite()).as("SameSite").isEqualTo(Session.Cookie.SameSite.LAX);
            assertThat(cookie.getName()).as("cookie name").isEqualTo("SOCIETIXSESSION");
        }
    }

    @SpringBootTest(classes = SessionCookieConfigTest.PropsOnlyConfig.class)
    @ActiveProfiles("prod")
    @DisplayName("session cookie attributes (prod)")
    static class Prod {

        @Autowired
        private ServerProperties serverProperties;

        @Test
        @DisplayName("prod pins the session cookie Secure flag to true")
        void prodPinsSecureCookie() {
            Session.Cookie cookie = serverProperties.getServlet().getSession().getCookie();
            assertThat(cookie.getSecure()).as("Secure (prod)").isTrue();
            // HttpOnly/SameSite inherited from the base config still hold in prod.
            assertThat(cookie.getHttpOnly()).as("HttpOnly (prod)").isTrue();
            assertThat(cookie.getSameSite()).as("SameSite (prod)").isEqualTo(Session.Cookie.SameSite.LAX);
        }
    }

    /**
     * Binds only {@link ServerProperties}; no auto-configuration that needs a datasource runs.
     */
    @Configuration
    @EnableConfigurationProperties(ServerProperties.class)
    static class PropsOnlyConfig {
    }
}
