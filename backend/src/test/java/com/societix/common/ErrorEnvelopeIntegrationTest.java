package com.societix.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.societix.common.api.ConflictException;
import com.societix.common.api.LoggingContext;
import com.societix.common.api.NotFoundException;
import com.societix.common.api.ProblemDetail;
import com.societix.common.api.RateLimitExceededException;
import com.societix.common.internal.CorrelationIdFilter;
import com.societix.common.internal.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end contract tests for the error envelope ({@link ProblemDetail}) produced by
 * {@link GlobalExceptionHandler}, together with correlation-id propagation from
 * {@link CorrelationIdFilter} (tasks 2.1 + 2.2; Req 17.10, 21.1).
 *
 * <p>This is a deliberately lightweight slice: a standalone {@link MockMvc} wires a tiny
 * {@link ThrowingController} (one endpoint per mapped exception), the real
 * {@link GlobalExceptionHandler} as {@code @RestControllerAdvice}, and the real
 * {@link CorrelationIdFilter} in the chain. No Spring context or embedded servlet container is
 * started, yet the full contract the rest of the platform depends on is exercised:
 * <ul>
 *   <li>each mapped exception yields the correct HTTP status and the documented
 *       {@code application/problem+json} body shape (type/title/status/detail/instance);</li>
 *   <li>validation failures carry field-level {@code errors[]}; non-validation failures omit it;</li>
 *   <li>no response leaks a stack trace, internal identifier, or persistence detail (Req 17.10);</li>
 *   <li>the envelope's {@code correlationId} is populated from the MDC and equals the id the
 *       filter established for the request — i.e. it matches the request's log correlation id
 *       (Req 21.1), both for an inbound {@code X-Correlation-Id} and for a generated one.</li>
 * </ul>
 *
 * <p>The filter's own isolated behavior (header propagation, MDC cleanup, UUID generation) is
 * already covered by {@code CorrelationIdFilterTest}; this suite does not duplicate it.
 */
@DisplayName("Error envelope + correlation-id propagation")
class ErrorEnvelopeIntegrationTest {

    private static final String BASE_TYPE = "https://societix.app/errors";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    // ---------------------------------------------------------------------
    // (1) problem+json shape and HTTP status per mapped exception
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("renders the documented problem+json envelope for each mapped exception")
    class EnvelopeShape {

        @Test
        @DisplayName("validation failure -> 400 with field errors[] and problem+json content type")
        void validation() throws Exception {
            // Empty body -> @NotBlank on `name` fails -> MethodArgumentNotValidException.
            mockMvc.perform(post("/test/validation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/validation"))
                    .andExpect(jsonPath("$.title").value("Validation failed"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.detail").value("One or more fields are invalid."))
                    .andExpect(jsonPath("$.instance").value("/test/validation"))
                    // Field-level errors[] must identify the offending field (Req 17.3).
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("authentication failure -> 401 generic, no errors[]")
        void authentication() throws Exception {
            mockMvc.perform(post("/test/authentication"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/authentication"))
                    .andExpect(jsonPath("$.title").value("Authentication required"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("authorization denial -> 403 generic, no errors[]")
        void forbidden() throws Exception {
            mockMvc.perform(post("/test/forbidden"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/forbidden"))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.detail").value("You do not have permission to perform this operation."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("not-found / cross-tenant -> 404 with a fixed generic detail (no existence leak)")
        void notFound() throws Exception {
            mockMvc.perform(post("/test/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/not-found"))
                    .andExpect(jsonPath("$.title").value("Not found"))
                    .andExpect(jsonPath("$.status").value(404))
                    // The fixed generic detail is used, NOT the exception message, so a caller cannot
                    // probe for a protected resource's existence (Req 1.7, 17.10).
                    .andExpect(jsonPath("$.detail").value("The requested resource was not found."))
                    .andExpect(jsonPath("$.detail").value(not(containsString("secret-flat-42"))))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("conflict -> 409 with the exception's generic message")
        void conflict() throws Exception {
            mockMvc.perform(post("/test/conflict"))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/conflict"))
                    .andExpect(jsonPath("$.title").value("Conflict"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.detail").value("A payment with that reference already exists."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("rate limit -> 429 with the exception's generic message")
        void rateLimit() throws Exception {
            mockMvc.perform(post("/test/rate-limit"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/rate-limit"))
                    .andExpect(jsonPath("$.title").value("Too many requests"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.detail").value("Too many requests. Please retry later."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }

        @Test
        @DisplayName("unanticipated error -> 500 generic, with no stack trace or internal detail")
        void internal() throws Exception {
            mockMvc.perform(post("/test/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value(BASE_TYPE + "/internal"))
                    .andExpect(jsonPath("$.title").value("Internal server error"))
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    // ---------------------------------------------------------------------
    // (1b) non-leakage guarantees (Req 17.10)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("never leaks internal details in the error body (Req 17.10)")
    class NonLeakage {

        @Test
        @DisplayName("the 500 body contains no stack trace, exception class name, or internal message")
        void internalErrorHidesEverything() throws Exception {
            String body = mockMvc.perform(post("/test/boom"))
                    .andExpect(status().isInternalServerError())
                    .andReturn().getResponse().getContentAsString();

            // The thrown exception carried a sensitive internal detail; none of it may surface.
            org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain("db password = hunter2")       // the internal exception message
                    .doesNotContain("IllegalStateException")        // the exception class name
                    .doesNotContain("java.lang.")                   // any FQCN from a stack trace
                    .doesNotContain("at com.societix")              // stack-trace frames
                    .doesNotContain("Caused by")                    // stack-trace cause chain
                    .doesNotContain("trace")                        // no "trace"/"stackTrace" field
                    .doesNotContain("exception");                   // no "exception" field (Spring's default error attr)
        }

        @Test
        @DisplayName("the 404 body does not echo the internal identifier from the exception message")
        void notFoundHidesInternalId() throws Exception {
            String body = mockMvc.perform(post("/test/not-found"))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain("secret-flat-42")
                    .doesNotContain("NotFoundException")
                    .doesNotContain("at com.societix");
        }
    }

    // ---------------------------------------------------------------------
    // (2) correlationId matches the request's log correlation id (Req 21.1)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("populates correlationId from the request's correlation id (Req 21.1)")
    class CorrelationIdPropagation {

        @Test
        @DisplayName("an inbound X-Correlation-Id appears verbatim in the error envelope and echoes on the response header")
        void inboundCorrelationIdFlowsIntoEnvelope() throws Exception {
            String inbound = "corr-inbound-abc-123";

            mockMvc.perform(post("/test/not-found")
                            .header(LoggingContext.CORRELATION_ID_HEADER, inbound))
                    .andExpect(status().isNotFound())
                    // The envelope's correlationId equals the id the filter put on the MDC (= request id).
                    .andExpect(jsonPath("$.correlationId").value(inbound))
                    // And the same id is echoed on the response header the filter sets.
                    .andExpect(header().string(LoggingContext.CORRELATION_ID_HEADER, inbound));
        }

        @Test
        @DisplayName("a generated correlation id is identical in the response header and the error envelope")
        void generatedCorrelationIdMatchesHeaderAndEnvelope() throws Exception {
            // No inbound header: the filter mints a UUID. The envelope's correlationId must equal
            // the generated id that the filter also echoes on the response header — proving the
            // error envelope carries the very same correlation id used for this request's logs.
            var result = mockMvc.perform(post("/test/conflict"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.correlationId").isNotEmpty())
                    .andReturn();

            String headerId = result.getResponse().getHeader(LoggingContext.CORRELATION_ID_HEADER);
            org.assertj.core.api.Assertions.assertThat(headerId).isNotBlank();

            // The body's correlationId must equal the header id from the SAME response.
            String body = result.getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(body)
                    .contains("\"correlationId\":\"" + headerId + "\"");
        }
    }

    // ---------------------------------------------------------------------
    // Tiny controller: one endpoint per mapped exception.
    // ---------------------------------------------------------------------

    @RestController
    static class ThrowingController {

        @PostMapping("/test/validation")
        String validation(@Valid @RequestBody NameRequest request) {
            return "ok";
        }

        @PostMapping("/test/authentication")
        String authentication() {
            throw new TestAuthenticationException();
        }

        @PostMapping("/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("internal: user 7 lacks ROLE_X on object 9");
        }

        @PostMapping("/test/not-found")
        String notFound() {
            // Carries an internal id in the message; the handler must NOT surface it.
            throw new NotFoundException("flat secret-flat-42 not visible to tenant");
        }

        @PostMapping("/test/conflict")
        String conflict() {
            throw new ConflictException("A payment with that reference already exists.");
        }

        @PostMapping("/test/rate-limit")
        String rateLimit() {
            // Blank message -> handler falls back to its generic default.
            throw new RateLimitExceededException("  ");
        }

        @PostMapping("/test/boom")
        String boom() {
            // Unanticipated: sensitive internal detail that must never reach the client.
            throw new IllegalStateException("db password = hunter2");
        }
    }

    /** A concrete {@link AuthenticationException} (the Spring class is abstract). */
    static class TestAuthenticationException extends AuthenticationException {
        TestAuthenticationException() {
            super("bad credentials for user admin@example.com");
        }
    }

    /** Request body for the validation endpoint. */
    record NameRequest(@NotBlank String name) {
    }
}
