package com.societix.audit.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.societix.common.api.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuditQuery} (task 11.1).
 *
 * <p>Confirms the query normalizes its paging parameters to the platform's shared bounds
 * ({@link PageQuery}) so audit listing stays consistent and cost-bounded, and carries no tenant
 * identifier (scope is a server concern, Req 16.5).
 */
@DisplayName("AuditQuery")
class AuditQueryTest {

    @Test
    @DisplayName("clamps an oversized page size to the shared maximum")
    void clampsOversizedSize() {
        AuditQuery query = new AuditQuery(0, PageQuery.MAX_SIZE + 500);
        assertThat(query.size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    @DisplayName("substitutes the default size for a non-positive size")
    void defaultsNonPositiveSize() {
        AuditQuery query = new AuditQuery(3, 0);
        assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(query.page()).isEqualTo(3);
    }

    @Test
    @DisplayName("normalizes a negative page index to zero")
    void normalizesNegativePage() {
        AuditQuery query = new AuditQuery(-5, 20);
        assertThat(query.page()).isEqualTo(PageQuery.DEFAULT_PAGE);
    }

    @Test
    @DisplayName("firstPage() uses the platform defaults")
    void firstPageUsesDefaults() {
        AuditQuery query = AuditQuery.firstPage();
        assertThat(query.page()).isEqualTo(PageQuery.DEFAULT_PAGE);
        assertThat(query.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }
}
