package com.societix.common.api;

/**
 * The platform's standard pagination and sorting query-parameter conventions for list endpoints
 * (design.md "REST Conventions" — Pagination/filtering/sorting).
 *
 * <p>This is a documentation-and-defaults holder for the shared parameter <em>names</em> and
 * bounds. It is intentionally not a bound request object: controllers in later milestones bind
 * these parameters however is natural for their framework (e.g. Spring's {@code Pageable} or
 * explicit {@code @RequestParam}s) and use {@link #clampSize(int)} / the default constants here so
 * every list endpoint behaves consistently. The resulting page is returned as a
 * {@link PageResponse}.
 *
 * <h2>Parameters</h2>
 * <ul>
 *   <li>{@value #PARAM_PAGE} — zero-based page index; defaults to {@value #DEFAULT_PAGE}.</li>
 *   <li>{@value #PARAM_SIZE} — page size; defaults to {@value #DEFAULT_SIZE}, capped at
 *       {@value #MAX_SIZE} to bound result-set cost.</li>
 *   <li>{@value #PARAM_SORT} — sort instruction in the form {@code field,(asc|desc)}; may repeat
 *       for multi-field sorts (e.g. {@code sort=createdAt,desc&sort=name,asc}).</li>
 * </ul>
 *
 * <p>Example: {@code GET /api/v1/invoices?page=0&size=20&sort=issuedAt,desc}.
 *
 * <p>The society/tenant scope is <strong>never</strong> a client parameter — it is applied
 * server-side from the authenticated session (Req 1.8, BR1, BR10).
 */
public final class PageQuery {

    /** Query-parameter name for the zero-based page index: {@value}. */
    public static final String PARAM_PAGE = "page";
    /** Query-parameter name for the page size: {@value}. */
    public static final String PARAM_SIZE = "size";
    /** Query-parameter name for a sort instruction ({@code field,(asc|desc)}): {@value}. */
    public static final String PARAM_SORT = "sort";

    /** Default page index when {@value #PARAM_PAGE} is absent. */
    public static final int DEFAULT_PAGE = 0;
    /** Default page size when {@value #PARAM_SIZE} is absent. */
    public static final int DEFAULT_SIZE = 20;
    /** Upper bound on page size; larger requested sizes are clamped to this value. */
    public static final int MAX_SIZE = 100;

    private PageQuery() {
        // Constants holder; not instantiable.
    }

    /**
     * Clamps a requested page size into the inclusive range {@code [1, }{@value #MAX_SIZE}{@code ]},
     * substituting {@link #DEFAULT_SIZE} for non-positive requests. Keeps every list endpoint's
     * page-size handling uniform and bounds worst-case query cost.
     *
     * @param requestedSize the size requested by the client (may be out of range)
     * @return a size within {@code [1, }{@value #MAX_SIZE}{@code ]}
     */
    public static int clampSize(int requestedSize) {
        if (requestedSize <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(requestedSize, MAX_SIZE);
    }

    /**
     * Normalizes a requested page index to a non-negative value, substituting {@link #DEFAULT_PAGE}
     * for negative requests.
     *
     * @param requestedPage the zero-based page index requested by the client
     * @return a non-negative page index
     */
    public static int normalizePage(int requestedPage) {
        return Math.max(requestedPage, DEFAULT_PAGE);
    }
}
