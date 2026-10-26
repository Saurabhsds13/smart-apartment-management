package com.societix.common.api;

/**
 * Signals that a client or account exceeded its configured request-rate limit within the
 * configured window (Req 17.4; design.md "Error Handling"). Mapped to HTTP
 * {@code 429 Too Many Requests} by the global exception handler, which rejects the excess request
 * without processing it.
 *
 * <p>The {@code detail} message must be generic and MUST NOT expose internal rate-limiter state.
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Creates a rate-limit exception with a generic, client-safe message.
     *
     * @param message a generic, client-safe description
     */
    public RateLimitExceededException(String message) {
        super(message);
    }
}
