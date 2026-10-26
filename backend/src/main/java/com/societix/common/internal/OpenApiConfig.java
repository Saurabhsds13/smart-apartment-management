package com.societix.common.internal;

import com.societix.common.api.ApiConventions;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * springdoc / OpenAPI 3 configuration for the platform (design.md "Technology Stack" and
 * "REST Conventions"; Req 17.1).
 *
 * <p>springdoc generates the OpenAPI 3 document from the controllers and DTOs and serves it at
 * {@code /api/v1/api-docs} (see {@code application.yml} {@code springdoc.api-docs.path}). This bean
 * supplies the document's top-level metadata (title, description, version, contact, license) and a
 * single server entry pointing at the API base path, {@link ApiConventions#BASE_PATH}, so that the
 * "Try it out" host in Swagger UI and generated clients resolve to {@code /api/v1}.
 *
 * <h2>Swagger UI exposure (Req 17.1)</h2>
 * Swagger UI is gated by profile in {@code application.yml}: {@code springdoc.swagger-ui.enabled}
 * is {@code true} under the {@code local} profile and {@code false} under {@code prod}. The design
 * calls for the UI to be available only in non-prod <em>or</em> behind a Platform_Admin. The
 * profile toggle delivers the non-prod half today; the Platform_Admin-gated path in production is
 * intentionally <strong>not</strong> wired here — it depends on Spring Security, which lands in
 * task 10.1. No authentication or authorization is implemented in this configuration.
 *
 * <p>This bean only customizes the generated document; it does not register endpoints, and it
 * lives in {@code common.internal} because it is an implementation detail of the shared module.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_TITLE = "Societix API";
    private static final String API_VERSION = "v1";
    private static final String API_DESCRIPTION = """
            REST API for Societix — a multi-tenant apartment and housing society management \
            platform. All endpoints are served under %s and are tenant-scoped on the server from \
            the authenticated session; the society id is never a path or query parameter. Errors \
            use the RFC 7807 application/problem+json envelope. See the shared REST conventions \
            (base path, pagination parameters, and status codes) in com.societix.common.api.\
            """.formatted(ApiConventions.BASE_PATH);

    /**
     * Builds the OpenAPI document metadata and server entry.
     *
     * <p>The server URL is {@link ApiConventions#BASE_PATH}, keeping the advertised API root in
     * lockstep with the base path every controller is mounted on.
     *
     * @return the customized {@link OpenAPI} document consumed by springdoc
     */
    @Bean
    public OpenAPI societixOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .description(API_DESCRIPTION)
                        .version(API_VERSION)
                        .contact(new Contact()
                                .name("Societix Platform Team")
                                .email("support@societix.app"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://societix.app")))
                .servers(List.of(new Server()
                        .url(ApiConventions.BASE_PATH)
                        .description("Societix API (version v1)")));
    }
}
