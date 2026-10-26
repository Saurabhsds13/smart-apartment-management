package com.societix.common.api;

/**
 * Signals that a request conflicts with the current state of a resource — for example a duplicate
 * invoice run, a duplicate payment reference, or a booking overlap (design.md "Error Handling";
 * Req 17.10). Mapped to HTTP {@code 409 Conflict} by the global exception handler.
 *
 * <p>The {@code detail} message must be a generic, client-safe explanation of the conflict and
 * MUST NOT expose internal identifiers or persistence details.
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates a conflict exception with a generic, client-safe message.
     *
     * @param message a generic, client-safe description of the conflict
     */
    public ConflictException(String message) {
        super(message);
    }
}
