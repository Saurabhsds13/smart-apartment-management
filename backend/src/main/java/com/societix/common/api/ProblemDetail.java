package com.societix.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The platform-wide RFC 7807 {@code application/problem+json} error envelope.
 *
 * <p>This is the single, documented shape for <strong>every</strong> error response
 * (design.md "Error Envelope (RFC 7807 problem+json)"). It deliberately exposes only
 * client-safe information: no stack traces, no internal/database identifiers, and no messages
 * that reveal whether a protected resource exists (Req 17.10).
 *
 * <p>Fields map to RFC 7807 plus two platform extensions:
 * <ul>
 *   <li>{@code type} — a URI identifying the error category (e.g.
 *       {@code https://societix.app/errors/validation}).</li>
 *   <li>{@code title} — a short, human-readable summary of the error category.</li>
 *   <li>{@code status} — the HTTP status code, duplicated in the body per RFC 7807.</li>
 *   <li>{@code detail} — a human-readable, client-safe explanation of this occurrence.</li>
 *   <li>{@code instance} — the request path that produced the error.</li>
 *   <li>{@code correlationId} — <em>extension.</em> Matches the request's log correlation id
 *       (design.md; Req 21.1). Populated from the MDC key {@value #CORRELATION_ID_MDC_KEY},
 *       which is set by the correlation-id servlet filter (task 2.2).</li>
 *   <li>{@code errors} — <em>extension.</em> Optional field-level validation errors; omitted
 *       (serialized as absent) for non-validation failures.</li>
 * </ul>
 *
 * <p>{@code null} properties are omitted from the JSON so, for example, a 403 response carries no
 * empty {@code errors} array.
 *
 * @param type          URI identifying the error category
 * @param title         short, human-readable summary of the category
 * @param status        HTTP status code
 * @param detail        client-safe, human-readable explanation of this occurrence
 * @param instance      the request path that produced the error
 * @param correlationId request correlation id, sourced from MDC (see {@link #CORRELATION_ID_MDC_KEY})
 * @param errors        optional field-level validation errors (validation failures only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId,
        List<ApiFieldError> errors) {

    /**
     * Base URI for all documented error {@code type} values.
     * The concrete type is {@code BASE_TYPE + "/" + category} (e.g. {@code .../validation}).
     */
    public static final String BASE_TYPE = "https://societix.app/errors";

    /**
     * The MDC key under which the correlation-id servlet filter (task 2.2) stores the per-request
     * correlation id. Shared here so the filter and the exception handler agree on one name
     * without a compile-time dependency between them.
     */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Builds the error {@code type} URI for a category, e.g. {@code typeUri("validation")} yields
     * {@code https://societix.app/errors/validation}.
     *
     * @param category the error category slug
     * @return the fully-qualified error type URI
     */
    public static String typeUri(String category) {
        return BASE_TYPE + "/" + category;
    }
}
