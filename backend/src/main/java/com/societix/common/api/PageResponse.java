package com.societix.common.api;

import java.util.List;
import java.util.Objects;

/**
 * The platform's standard paginated list-response shape (design.md "REST Conventions" — responses
 * carry page metadata).
 *
 * <p>Every list endpoint returns this envelope so clients see a uniform structure: the page's
 * {@code content} plus the metadata needed to render pagination controls and request the next
 * page using the {@link PageQuery} parameters. Contents are always the tenant-scoped view models
 * for the current session (Req 1.8, BR10); this type carries no society identifier because tenant
 * scope is a server concern, not part of the wire contract.
 *
 * @param <T>           the element (view-model) type carried in {@link #content}
 * @param content       the items on this page, in sort order; never {@code null} (empty for an
 *                      out-of-range or zero-result page)
 * @param page          the zero-based index of this page
 * @param size          the page size that was applied (see {@link PageQuery#clampSize(int)})
 * @param totalElements the total number of matching elements across all pages
 * @param totalPages    the total number of pages for the applied {@code size}
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * Canonical constructor that defensively copies {@code content} into an immutable list and
     * rejects a {@code null} content list.
     */
    public PageResponse {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    /**
     * Builds a {@code PageResponse} from the current page's content, the applied paging
     * parameters, and the total element count, deriving {@code totalPages} consistently.
     *
     * @param content       the items on this page (must not be {@code null})
     * @param page          the zero-based page index
     * @param size          the applied page size (must be positive)
     * @param totalElements the total number of matching elements across all pages
     * @param <T>           the element type
     * @return a populated {@code PageResponse}
     * @throws IllegalArgumentException if {@code size} is not positive
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }

    /**
     * Builds an empty, zero-result page — the standard response for a list query that matches no
     * rows (e.g. an empty tenant), as opposed to an error.
     *
     * @param size the applied page size (must be positive)
     * @param <T>  the element type
     * @return an empty {@code PageResponse} on page {@link PageQuery#DEFAULT_PAGE}
     */
    public static <T> PageResponse<T> empty(int size) {
        return of(List.of(), PageQuery.DEFAULT_PAGE, size, 0L);
    }
}
