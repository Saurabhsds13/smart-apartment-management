package com.societix.common.api;

import java.util.List;

/**
 * Signals that caller-supplied input failed a <em>service-layer</em> validation rule — a check that
 * is enforced in domain code rather than by a Bean Validation annotation on a controller argument
 * (design.md "Error Handling"; Req 17.3). Mapped to HTTP {@code 400 Bad Request} by the global
 * exception handler, carrying one {@link ApiFieldError} per offending field.
 *
 * <p>Use this when a rule cannot be expressed as a declarative {@code @Valid} constraint — for
 * example a password-policy check performed inside the identity module (Req 2.3), where the raw
 * value must never be echoed back. Like every error on the platform, the {@code message} and the
 * field errors MUST stay generic and client-safe: they describe <em>that</em> a field was invalid,
 * never the rejected value itself and never internal identifiers or persistence details (Req 17.10).
 */
public class ValidationException extends RuntimeException {

    /** Client-safe, field-level descriptions of what failed. Never carries the rejected value. */
    private final transient List<ApiFieldError> fieldErrors;

    /**
     * Creates a validation exception for a single offending field.
     *
     * @param field   the name of the request field that failed validation (e.g. {@code "password"})
     * @param code    a stable, machine-readable reason code (e.g. {@code "length-out-of-range"})
     * @param message a human-readable, client-safe explanation that does NOT echo the rejected value
     */
    public ValidationException(String field, String code, String message) {
        this(message, List.of(new ApiFieldError(field, code, message)));
    }

    /**
     * Creates a validation exception for one or more offending fields.
     *
     * @param message     a generic, client-safe summary message
     * @param fieldErrors the per-field errors; copied defensively and never {@code null}
     */
    public ValidationException(String message, List<ApiFieldError> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /**
     * Returns the client-safe, field-level errors describing what failed.
     *
     * @return an unmodifiable list of field errors (possibly empty, never {@code null})
     */
    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}
