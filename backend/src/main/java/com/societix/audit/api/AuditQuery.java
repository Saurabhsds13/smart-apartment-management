package com.societix.audit.api;

import com.societix.common.api.PageQuery;

/**
 * Pagination parameters for a tenant-scoped audit query (task 11.1; design.md "REST Conventions",
 * "Audit"; Req 16.5).
 *
 * <p>Deliberately carries <strong>no</strong> society identifier. Tenant scope for an audit query
 * is resolved server-side from the authenticated session via {@code TenantContext} — never from
 * client input (Req 1.8, 16.5, BR1). A reviewer sees only their own tenant's records; a
 * Platform_Admin acting at platform scope is the sole exception (Req 16.5). The service applies
 * that scope; this object only expresses which page of results the caller wants.
 *
 * @param page the zero-based page index (normalized via {@link PageQuery#normalizePage(int)})
 * @param size the requested page size (clamped via {@link PageQuery#clampSize(int)})
 */
public record AuditQuery(int page, int size) {

    /**
     * Normalizes the page index and clamps the page size to the platform's shared bounds so every
     * caller gets consistent, cost-bounded paging.
     */
    public AuditQuery {
        page = PageQuery.normalizePage(page);
        size = PageQuery.clampSize(size);
    }

    /**
     * A query for the first page at the platform default page size.
     *
     * @return a default {@code AuditQuery}
     */
    public static AuditQuery firstPage() {
        return new AuditQuery(PageQuery.DEFAULT_PAGE, PageQuery.DEFAULT_SIZE);
    }
}
