package com.societix.common.internal;

import com.societix.common.api.ApiFieldError;
import com.societix.common.api.ConflictException;
import com.societix.common.api.NotFoundException;
import com.societix.common.api.ProblemDetail;
import com.societix.common.api.RateLimitExceededException;
import com.societix.common.api.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global exception handler that renders every error as the platform-wide RFC 7807
 * {@code application/problem+json} envelope ({@link ProblemDetail}).
 *
 * <p>Design references: design.md "Error Handling" and "Error Envelope (RFC 7807 problem+json)";
 * Req 17.3 (reject invalid input with a validation error identifying the offending fields and make
 * no state change) and Req 17.10 (deny without exposing sensitive system details).
 *
 * <h2>Non-leakage guarantees (Req 17.10)</h2>
 * <ul>
 *   <li>Stack traces are never serialized into a response; only generic, client-safe
 *       {@code detail} text is returned.</li>
 *   <li>Internal identifiers and persistence details are never placed in the body.</li>
 *   <li>Authorization failures map to {@code 403}/{@code 404} without revealing resource
 *       existence; the {@code NotFoundException} path reuses a fixed generic message.</li>
 *   <li>Unanticipated exceptions collapse to a generic {@code 500} with no detail about the
 *       underlying cause (the cause is logged server-side, not returned).</li>
 * </ul>
 *
 * <p>The {@code correlationId} field is populated from the MDC key
 * {@link ProblemDetail#CORRELATION_ID_MDC_KEY}, which the correlation-id servlet filter (task 2.2)
 * sets per request. When the filter is not yet active the field is simply absent.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Validation (400) ---------------------------------------------------

    /**
     * Handles request-body validation failures raised by {@code @Valid} on a controller argument.
     * Produces a {@code 400} envelope with one {@link ApiFieldError} per rejected field (Req 17.3).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiFieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toApiFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields are invalid.", request, fieldErrors);
    }

    /**
     * Handles constraint violations raised by validation on path/query parameters or on
     * method-level constraints. Produces a {@code 400} envelope with field-level errors (Req 17.3).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ApiFieldError> fieldErrors = ex.getConstraintViolations() == null
                ? List.of()
                : ex.getConstraintViolations().stream()
                        .map(GlobalExceptionHandler::toApiFieldError)
                        .toList();
        return build(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields are invalid.", request, fieldErrors);
    }

    /**
     * Handles service-layer validation failures raised in domain code (rules that cannot be
     * expressed as a declarative {@code @Valid} constraint — e.g. the identity password policy,
     * Req 2.3). Produces a {@code 400} envelope carrying the exception's client-safe field errors.
     * The rejected value is never echoed (Req 17.10).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            ValidationException ex, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "One or more fields are invalid.", request, ex.getFieldErrors());
    }

    // --- Authentication (401) ----------------------------------------------

    /**
     * Handles failed authentication. Produces a {@code 401} envelope with a generic message that
     * does not reveal why authentication failed (Req 17.10).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {

        return build(HttpStatus.UNAUTHORIZED, "authentication", "Authentication required",
                "Authentication is required to access this resource.", request, null);
    }

    // --- Authorization: forbidden (403) ------------------------------------

    /**
     * Handles authorization denials on resources the caller is known to not be permitted to act on.
     * Produces a {@code 403} envelope with no internal detail (Req 1.7, 17.10).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return build(HttpStatus.FORBIDDEN, "forbidden", "Forbidden",
                "You do not have permission to perform this operation.", request, null);
    }

    // --- Not found (404) ----------------------------------------------------

    /**
     * Handles missing resources <em>and</em> cross-tenant/unowned access, both reported as
     * {@code 404} so the response cannot be used to probe for a resource's existence
     * (Req 1.7, 17.10). A fixed generic detail is used rather than the exception message to avoid
     * leaking anything a caller might have embedded.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, "not-found", "Not found",
                "The requested resource was not found.", request, null);
    }

    // --- Conflict (409) -----------------------------------------------------

    /**
     * Handles state conflicts (duplicate invoice run, duplicate payment reference, booking overlap).
     * Produces a {@code 409} envelope using the exception's generic, client-safe message.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(
            ConflictException ex, HttpServletRequest request) {

        String detail = hasText(ex.getMessage())
                ? ex.getMessage()
                : "The request conflicts with the current state of the resource.";
        return build(HttpStatus.CONFLICT, "conflict", "Conflict", detail, request, null);
    }

    // --- Rate limit (429) ---------------------------------------------------

    /**
     * Handles requests rejected because the client/account exceeded its rate limit (Req 17.4).
     * Produces a {@code 429} envelope; the rejected request is not processed.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest request) {

        String detail = hasText(ex.getMessage())
                ? ex.getMessage()
                : "Too many requests. Please retry later.";
        return build(HttpStatus.TOO_MANY_REQUESTS, "rate-limit", "Too many requests",
                detail, request, null);
    }

    // --- Catch-all (500) ----------------------------------------------------

    /**
     * Last-resort handler for unanticipated exceptions. The cause is logged server-side (with the
     * correlation id via MDC) but never returned; the client receives a generic {@code 500} with no
     * stack trace or internal detail (Req 17.10).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(),
                request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal server error",
                "An unexpected error occurred.", request, null);
    }

    // --- Helpers ------------------------------------------------------------

    private static ResponseEntity<ProblemDetail> build(
            HttpStatus status, String category, String title, String detail,
            HttpServletRequest request, List<ApiFieldError> errors) {

        ProblemDetail body = new ProblemDetail(
                ProblemDetail.typeUri(category),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                MDC.get(ProblemDetail.CORRELATION_ID_MDC_KEY),
                errors == null || errors.isEmpty() ? null : errors);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        return new ApiFieldError(
                fieldError.getField(),
                fieldError.getCode(),
                fieldError.getDefaultMessage());
    }

    private static ApiFieldError toApiFieldError(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() == null
                ? null
                : lastNode(violation.getPropertyPath().toString());
        String code = violation.getConstraintDescriptor() == null
                ? null
                : violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return new ApiFieldError(field, code, violation.getMessage());
    }

    /** Returns the last segment of a (possibly dotted) property path, e.g. {@code create.amount -> amount}. */
    private static String lastNode(String propertyPath) {
        int idx = propertyPath.lastIndexOf('.');
        return idx >= 0 && idx < propertyPath.length() - 1
                ? propertyPath.substring(idx + 1)
                : propertyPath;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
