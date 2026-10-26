package com.societix.common.api;

/**
 * Signals that a requested resource does not exist, or that the caller is not entitled to know it
 * exists. Mapped to HTTP {@code 404 Not Found} by the global exception handler.
 *
 * <p><strong>Security note (Req 1.7, 17.10):</strong> cross-tenant or unowned object access is
 * deliberately reported as {@code 404} — indistinguishable from a genuinely nonexistent
 * resource — so the response never reveals whether a protected resource exists. The {@code detail}
 * message must stay generic and MUST NOT echo internal identifiers.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates a not-found exception with a generic, client-safe message. Callers MUST NOT include
     * internal identifiers or anything that confirms a protected resource exists.
     *
     * @param message a generic, client-safe description (e.g. {@code "Resource not found."})
     */
    public NotFoundException(String message) {
        super(message);
    }
}
