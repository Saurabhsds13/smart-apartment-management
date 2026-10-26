package com.societix.common.api;

import org.slf4j.MDC;

/**
 * Shared constants and helpers for the platform's structured-logging context (Req 21.1, 21.2).
 *
 * <p>Every request handled by the platform emits structured (JSON) log entries that carry a
 * per-request <em>correlation id</em> and, where it is known, the acting <em>society id</em>
 * (design.md "Audit Logging and Observability"). Those values are carried on the SLF4J
 * {@link MDC} (mapped diagnostic context) so that <strong>every</strong> log line produced while
 * handling a request is automatically tagged, without each log statement having to pass them
 * explicitly.
 *
 * <p>This class is the single source of truth for the MDC key names and the correlation-id HTTP
 * header, so the correlation-id servlet filter (task 2.2), the error envelope
 * ({@link ProblemDetail}), and the tenant-context filter (task 7.2) all agree on one spelling
 * without a compile-time dependency between them.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@link #CORRELATION_ID_MDC_KEY} ({@code correlationId}) — set once per request by the
 *       correlation-id filter; mirrored into the {@link ProblemDetail} error envelope.</li>
 *   <li>{@link #SOCIETY_ID_MDC_KEY} ({@code societyId}) — the tenant/society identifier, populated
 *       <em>where available</em>. Tenant resolution itself is not implemented here: tenancy is
 *       resolved server-side from the authenticated session by the {@code TenantContext} filter
 *       (task 7.2), which is the intended, single integration point that calls
 *       {@link #putSocietyId(String)}. Until that filter exists the key is simply absent, which is
 *       correct — Req 21.1 includes the society id "where applicable", not unconditionally.</li>
 * </ul>
 *
 * <p><strong>Logging hygiene (Req 21.4, 21.5).</strong> Only non-sensitive correlation metadata
 * belongs on the MDC. Never place secrets (passwords, tokens, MFA secrets, session ids) or full
 * personal-data records on the MDC or in any log message. Audit records are persisted by the
 * {@code Audit_Service} (task 11.1) inside the originating transaction and must <em>never</em> be
 * emitted as log lines in their place (Req 21.5).
 */
public final class LoggingContext {

    private LoggingContext() {
    }

    /**
     * MDC key for the per-request correlation id. Intentionally identical to
     * {@link ProblemDetail#CORRELATION_ID_MDC_KEY} so the filter that sets it and the error
     * envelope that reads it stay in lock-step.
     */
    public static final String CORRELATION_ID_MDC_KEY = ProblemDetail.CORRELATION_ID_MDC_KEY;

    /**
     * MDC key for the acting society (tenant) id. Populated where available (see class javadoc);
     * absent for pre-authentication requests and platform-scoped operations with no single society.
     */
    public static final String SOCIETY_ID_MDC_KEY = "societyId";

    /**
     * Inbound/outbound HTTP header carrying the correlation id. A caller (gateway, upstream service,
     * or test) may supply this to correlate a chain of requests; otherwise the filter generates one.
     * The same value is echoed back on the response so a client can report it when raising an issue.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /**
     * Puts the current society id on the MDC so subsequent log lines on this thread carry it.
     * Intended to be called by the tenant-context filter (task 7.2) once tenancy is resolved.
     * A {@code null} or blank value is ignored (the key stays absent) rather than logging an empty
     * field.
     *
     * @param societyId the resolved society id, or {@code null}/blank to leave the key unset
     */
    public static void putSocietyId(String societyId) {
        if (societyId != null && !societyId.isBlank()) {
            MDC.put(SOCIETY_ID_MDC_KEY, societyId);
        }
    }

    /**
     * Removes the society id from the MDC. Callers that set the society id mid-request should clear
     * it in a {@code finally} block; the correlation-id filter also defensively clears it at the end
     * of every request so a pooled worker thread never leaks one request's society id into the next.
     */
    public static void removeSocietyId() {
        MDC.remove(SOCIETY_ID_MDC_KEY);
    }
}
