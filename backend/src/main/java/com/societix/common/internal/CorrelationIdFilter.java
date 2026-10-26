package com.societix.common.internal;

import com.societix.common.api.LoggingContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes the per-request correlation id for structured logging
 * (Req 21.1, 21.2; design.md "Audit Logging and Observability").
 *
 * <p>For every request it:
 * <ol>
 *   <li>reads an inbound {@value com.societix.common.api.LoggingContext#CORRELATION_ID_HEADER}
 *       header if the caller supplied one (so a correlation id can be propagated across a chain of
 *       requests, Req 21.2), otherwise mints a fresh {@link UUID};</li>
 *   <li>puts that id on the SLF4J {@link MDC} under
 *       {@link LoggingContext#CORRELATION_ID_MDC_KEY} so <em>every</em> log line emitted while
 *       handling the request is tagged with it (Req 21.1), and so the error envelope
 *       ({@code ProblemDetail}) can surface the same id;</li>
 *   <li>echoes the id back on the response header so a client/operator can quote it when reporting
 *       an issue;</li>
 *   <li>clears the MDC in a {@code finally} block so a pooled worker thread never leaks one
 *       request's correlation id (or society id) into the next request.</li>
 * </ol>
 *
 * <h2>Ordering</h2>
 * The filter runs at {@link Ordered#HIGHEST_PRECEDENCE} so it is active <strong>before</strong> the
 * Spring Security filter chain and before any application logic. This guarantees that security
 * events (authentication/authorization failures) and every downstream log line already carry a
 * correlation id. The {@code society_id} is intentionally <em>not</em> resolved here: tenancy is
 * derived server-side from the authenticated session by the tenant-context filter (task 7.2), which
 * runs later in the chain and calls {@link LoggingContext#putSocietyId(String)} once it resolves the
 * society. That keeps this filter free of any tenancy concern while still guaranteeing MDC cleanup
 * for both keys.
 *
 * <h2>Logging hygiene (Req 21.4, 21.5)</h2>
 * Only the opaque correlation id (and, later, the society id) is placed on the MDC — never secrets
 * or personal data. This filter emits no log line containing request bodies, headers, or
 * credentials, and it never writes audit records: those are persisted by the {@code Audit_Service}
 * (task 11.1), never emitted as log lines (Req 21.5).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        MDC.put(LoggingContext.CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(LoggingContext.CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear both correlation keys so the worker thread is clean for the next request,
            // regardless of how the request completed (success, exception, or early return).
            MDC.remove(LoggingContext.CORRELATION_ID_MDC_KEY);
            LoggingContext.removeSocietyId();
        }
    }

    /**
     * Returns a usable correlation id for the request: the trimmed inbound header value when the
     * caller supplied a non-blank one (Req 21.2), otherwise a freshly generated UUID. The supplied
     * value is only read for correlation — it is never trusted for authorization or tenancy.
     */
    private static String resolveCorrelationId(HttpServletRequest request) {
        String inbound = request.getHeader(LoggingContext.CORRELATION_ID_HEADER);
        if (inbound != null && !inbound.isBlank()) {
            return inbound.trim();
        }
        return UUID.randomUUID().toString();
    }
}
