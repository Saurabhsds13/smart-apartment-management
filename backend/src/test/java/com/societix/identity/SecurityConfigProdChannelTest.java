package com.societix.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societix.common.internal.CorrelationIdFilter;
import com.societix.identity.internal.SecurityConfig;
import com.societix.identity.internal.SessionLifecycleFilter;
import com.societix.tenancy.internal.TenantContextFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Channel-security test for the {@code prod} profile (task 10.1; Req 17.9).
 *
 * <p>Under {@code prod}, {@link SecurityConfig} adds {@code requiresChannel().requiresSecure()} so a
 * request that did not arrive over an encrypted transport is rejected at the app edge rather than
 * processed. Spring Security's channel filter responds to an insecure request with a redirect to
 * the {@code https} equivalent URL — i.e. the plain-HTTP request is never dispatched to the
 * controller. This slice asserts that behaviour without Docker or a datasource.
 */
@WebMvcTest(
        controllers = SecurityConfigProdChannelTest.PingController.class,
        excludeFilters = {
            @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
            @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SessionLifecycleFilter.class),
            @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CorrelationIdFilter.class)
        })
@Import(SecurityConfig.class)
@ActiveProfiles("prod")
@DisplayName("SecurityConfig channel security (prod)")
class SecurityConfigProdChannelTest {

    @RestController
    static class PingController {
        @GetMapping("/api/v1/ping")
        String ping() {
            return "pong";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a request over unencrypted transport is redirected to https (rejected at the edge)")
    void insecureRequestIsRejected() throws Exception {
        // MockMvc issues an http request; requiresSecure() must refuse to process it over http and
        // instead redirect to the https URL, so the controller is never reached.
        mockMvc.perform(get("http://localhost/api/v1/ping"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://**"));
    }
}
