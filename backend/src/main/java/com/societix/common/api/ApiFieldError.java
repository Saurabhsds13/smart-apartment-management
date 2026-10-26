package com.societix.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single field-level validation error inside a {@link ProblemDetail} envelope.
 *
 * <p>Part of the public RFC 7807 contract (design.md "Error Envelope"): each entry identifies the
 * offending input ({@code field}), a stable machine-readable reason ({@code code}), and a
 * human-readable {@code message}. These describe <em>client input</em> only — they never carry
 * stack traces, internal identifiers, or persistence details (Req 17.10).
 *
 * @param field   the name of the request field that failed validation (e.g. {@code "amount"})
 * @param code    a stable, machine-readable error code (e.g. {@code "must-be-positive"})
 * @param message a human-readable explanation safe to show to the client
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiFieldError(String field, String code, String message) {
}
