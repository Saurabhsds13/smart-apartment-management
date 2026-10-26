package com.societix.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.societix.common.api.LoggingContext;
import com.societix.common.internal.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Focused unit tests for {@link CorrelationIdFilter} (Req 21.1, 21.2).
 *
 * <p>These are deliberately isolated: they drive the filter directly with Spring's mock
 * servlet request/response and a hand-rolled {@link FilterChain}, with no Spring context and no
 * HTTP stack. They assert the correlation-id contract the rest of the platform relies on:
 * <ul>
 *   <li>a correlation id is on the MDC under the agreed key <em>while</em> the chain runs (so every
 *       log line and the error envelope can read it);</li>
 *   <li>an inbound {@code X-Correlation-Id} header is propagated rather than replaced (Req 21.2);</li>
 *   <li>a fresh UUID is generated when no header is supplied;</li>
 *   <li>the id is echoed on the response header;</li>
 *   <li>the MDC is cleared after the request — even when the chain throws — so a pooled thread
 *       never leaks one request's context into the next.</li>
 * </ul>
 *
 * <p>The error-envelope / end-to-end correlation-id propagation assertions are owned by task 2.5;
 * this suite stays scoped to the filter's own behavior.
 */
@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("propagates an inbound X-Correlation-Id onto the MDC and the response header")
    void propagatesInboundCorrelationId() throws ServletException, IOException {
        String inbound = "inbound-corr-" + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(LoggingContext.CORRELATION_ID_HEADER, inbound);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) ->
                seenInsideChain.set(MDC.get(LoggingContext.CORRELATION_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain.get()).isEqualTo(inbound);
        assertThat(response.getHeader(LoggingContext.CORRELATION_ID_HEADER)).isEqualTo(inbound);
    }

    @Test
    @DisplayName("generates a UUID correlation id when no inbound header is present")
    void generatesCorrelationIdWhenHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) ->
                seenInsideChain.set(MDC.get(LoggingContext.CORRELATION_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        String generated = seenInsideChain.get();
        assertThat(generated).isNotBlank();
        // A valid UUID is expected; parsing throws if it is not.
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(response.getHeader(LoggingContext.CORRELATION_ID_HEADER)).isEqualTo(generated);
    }

    @Test
    @DisplayName("ignores a blank inbound header and generates a fresh id instead")
    void ignoresBlankInboundHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(LoggingContext.CORRELATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) ->
                seenInsideChain.set(MDC.get(LoggingContext.CORRELATION_ID_MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(UUID.fromString(seenInsideChain.get())).isNotNull();
    }

    @Test
    @DisplayName("clears the correlation id and society id from the MDC after the request completes")
    void clearsMdcAfterRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate a downstream filter (e.g. the future tenant-context filter) setting a society id.
        FilterChain chain = (req, res) -> LoggingContext.putSocietyId("society-123");

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(LoggingContext.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("clears the MDC even when the downstream chain throws")
    void clearsMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException | IOException expected) {
            // The filter must propagate the failure but still clean up the MDC.
        }

        assertThat(MDC.get(LoggingContext.CORRELATION_ID_MDC_KEY)).isNull();
        assertThat(MDC.get(LoggingContext.SOCIETY_ID_MDC_KEY)).isNull();
    }
}
