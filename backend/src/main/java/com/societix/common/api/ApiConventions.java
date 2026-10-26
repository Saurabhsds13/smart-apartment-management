package com.societix.common.api;

/**
 * The platform's shared, documented REST conventions (design.md "REST Conventions").
 *
 * <p>This is a constants-and-documentation holder, not a controller or framework component. It
 * exists so that every module references <em>one</em> authoritative definition of the API base
 * path, versioning policy, pagination parameter names, and the HTTP status-code contract — rather
 * than re-deriving them per module. Controllers added in later milestones should build their
 * request mappings on {@link #BASE_PATH} and document their list endpoints against
 * {@link PageQuery} / {@link PageResponse}.
 *
 * <h2>Base path &amp; versioning (Req 17.1)</h2>
 * All HTTP endpoints live under {@value #BASE_PATH}. Breaking changes introduce a new major
 * version prefix (e.g. {@code /api/v2}); purely additive changes stay within v1. The OpenAPI
 * document (served at {@code /api/v1/api-docs}, see {@code application.yml} {@code springdoc.*})
 * advertises {@value #BASE_PATH} as its single server entry via
 * {@link com.societix.common.internal.OpenApiConfig}.
 *
 * <h2>Resource naming</h2>
 * Plural nouns, hierarchical where natural (e.g. {@code /api/v1/buildings/{buildingId}/flats}).
 * The society id is <strong>never</strong> part of the path or query — tenant scope is derived
 * on the server from the authenticated session (Req 1.2, BR1).
 *
 * <h2>HTTP method &amp; status-code contract</h2>
 * The success and error status codes below are the platform-wide contract. Error responses always
 * carry the {@link ProblemDetail} {@code application/problem+json} envelope (Req 17.10).
 * <table border="1">
 *   <caption>Standard status codes</caption>
 *   <tr><th>Situation</th><th>Status</th></tr>
 *   <tr><td>{@code GET} success</td><td>{@value #STATUS_OK} OK</td></tr>
 *   <tr><td>{@code POST} created (with {@code Location} header)</td><td>{@value #STATUS_CREATED} Created</td></tr>
 *   <tr><td>{@code PUT}/{@code PATCH} success</td><td>{@value #STATUS_OK} OK</td></tr>
 *   <tr><td>{@code DELETE} success</td><td>{@value #STATUS_NO_CONTENT} No Content</td></tr>
 *   <tr><td>Server-side validation failure (Req 17.3)</td><td>{@value #STATUS_BAD_REQUEST} Bad Request</td></tr>
 *   <tr><td>Unauthenticated</td><td>{@value #STATUS_UNAUTHORIZED} Unauthorized</td></tr>
 *   <tr><td>Authenticated but forbidden</td><td>{@value #STATUS_FORBIDDEN} Forbidden</td></tr>
 *   <tr><td>Not found — also the cross-tenant / unowned response (Req 1.7)</td><td>{@value #STATUS_NOT_FOUND} Not Found</td></tr>
 *   <tr><td>Conflict (duplicate invoice, booking overlap, duplicate payment ref)</td><td>{@value #STATUS_CONFLICT} Conflict</td></tr>
 *   <tr><td>Rate limit exceeded (Req 17.4)</td><td>{@value #STATUS_TOO_MANY_REQUESTS} Too Many Requests</td></tr>
 * </table>
 *
 * <h2>Pagination, filtering &amp; sorting</h2>
 * List endpoints accept the {@link PageQuery} query parameters ({@code page}, {@code size},
 * {@code sort}) and return a {@link PageResponse}. All lists are tenant-scoped (Req 1.8, BR10);
 * the tenant filter is applied server-side and is not expressible as a client parameter.
 *
 * <h2>DTO patterns</h2>
 * Request DTOs carry Bean Validation annotations; response DTOs are explicit view models. JPA
 * entities are never serialized directly.
 */
public final class ApiConventions {

    /**
     * The single base path and version prefix for every HTTP endpoint: {@value}.
     * Controllers compose their mappings on top of this (e.g. {@code BASE_PATH + "/invoices"}).
     */
    public static final String BASE_PATH = "/api/v1";

    /** {@code GET}/{@code PUT}/{@code PATCH} success. */
    public static final int STATUS_OK = 200;
    /** {@code POST} created a resource; the response carries a {@code Location} header. */
    public static final int STATUS_CREATED = 201;
    /** {@code DELETE} success; no response body. */
    public static final int STATUS_NO_CONTENT = 204;
    /** Server-side validation failure (Req 17.3). */
    public static final int STATUS_BAD_REQUEST = 400;
    /** Request is not authenticated. */
    public static final int STATUS_UNAUTHORIZED = 401;
    /** Authenticated caller lacks permission for the operation. */
    public static final int STATUS_FORBIDDEN = 403;
    /** Resource not found — also the deliberate cross-tenant / unowned response (Req 1.7). */
    public static final int STATUS_NOT_FOUND = 404;
    /** State conflict (duplicate invoice, booking overlap, duplicate payment reference). */
    public static final int STATUS_CONFLICT = 409;
    /** Per-client request-rate limit exceeded (Req 17.4). */
    public static final int STATUS_TOO_MANY_REQUESTS = 429;

    private ApiConventions() {
        // Constants holder; not instantiable.
    }
}
