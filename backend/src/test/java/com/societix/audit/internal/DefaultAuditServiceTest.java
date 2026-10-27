package com.societix.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditQuery;
import com.societix.audit.api.AuditRecordView;
import com.societix.common.api.PageResponse;
import com.societix.tenancy.api.TenantContext;

/**
 * Unit tests for {@link DefaultAuditService} (task 11.1; Req 16.1-16.5).
 *
 * <p>The repository and {@link TenantContext} are mocked so the suite focuses on the service's own
 * behavior: it maps an {@link AuditEntry} into an append-only insert, and it applies tenant scope
 * from the session on read — platform-wide for a Platform_Admin, Society-scoped for a tenant
 * reviewer, and an empty page when no scope is resolved (Req 1.6, 16.5). Transaction propagation
 * ({@code MANDATORY}) is an integration concern verified by task 11.2/11.3 against a real context;
 * here we assert the logic the annotations wrap.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAuditService")
class DefaultAuditServiceTest {

    @Mock
    private AuditRecordRepository repository;

    @Mock
    private TenantContext tenantContext;

    @InjectMocks
    private DefaultAuditService service;

    @Test
    @DisplayName("record maps the entry to an append-only insert")
    void recordInserts() {
        UUID society = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        AuditEntry entry = AuditEntry.builder()
                .societyId(society)
                .actorUserId(actor)
                .action("PAYMENT_RECORDED")
                .targetType("Invoice")
                .targetId("inv-1")
                .afterSummary("{\"balance\":0}")
                .build();

        service.record(entry);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());
        AuditRecord saved = captor.getValue();
        assertThat(saved.getSocietyId()).isEqualTo(society);
        assertThat(saved.getActorUserId()).isEqualTo(actor);
        assertThat(saved.getAction()).isEqualTo("PAYMENT_RECORDED");
        assertThat(saved.getTargetType()).isEqualTo("Invoice");
        assertThat(saved.getTargetId()).isEqualTo("inv-1");
        assertThat(saved.getAfterSummary()).isEqualTo("{\"balance\":0}");
    }

    @Test
    @DisplayName("query for a tenant reviewer scopes to their own society (Req 16.5)")
    void queryScopesToTenant() {
        UUID society = UUID.randomUUID();
        when(tenantContext.isPlatformAdmin()).thenReturn(false);
        when(tenantContext.isResolved()).thenReturn(true);
        when(tenantContext.currentSocietyId()).thenReturn(society);
        when(repository.findBySocietyId(eq(society), any(Pageable.class)))
                .thenReturn(pageOf(auditRow(society)));

        PageResponse<AuditRecordView> result = service.query(AuditQuery.firstPage());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).societyId()).isEqualTo(society);
        // Tenant reviewers never hit the platform-wide read.
        verify(repository, never()).findAllBy(any(Pageable.class));
    }

    @Test
    @DisplayName("query newest-first ordering is applied")
    void queryOrdersNewestFirst() {
        UUID society = UUID.randomUUID();
        when(tenantContext.isPlatformAdmin()).thenReturn(false);
        when(tenantContext.isResolved()).thenReturn(true);
        when(tenantContext.currentSocietyId()).thenReturn(society);
        when(repository.findBySocietyId(eq(society), any(Pageable.class)))
                .thenReturn(pageOf(auditRow(society)));

        service.query(AuditQuery.firstPage());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findBySocietyId(eq(society), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("occurredAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("query for a Platform_Admin reads platform-wide (Req 16.5)")
    void queryPlatformScope() {
        when(tenantContext.isPlatformAdmin()).thenReturn(true);
        when(repository.findAllBy(any(Pageable.class)))
                .thenReturn(pageOf(auditRow(null), auditRow(UUID.randomUUID())));

        PageResponse<AuditRecordView> result = service.query(AuditQuery.firstPage());

        assertThat(result.content()).hasSize(2);
        // Platform scope never narrows to a single society.
        verify(repository, never()).findBySocietyId(any(UUID.class), any(Pageable.class));
    }

    @Test
    @DisplayName("query with no resolved tenant returns an empty page without touching storage (Req 1.6)")
    void queryUnresolvedReturnsEmpty() {
        when(tenantContext.isPlatformAdmin()).thenReturn(false);
        when(tenantContext.isResolved()).thenReturn(false);

        PageResponse<AuditRecordView> result = service.query(AuditQuery.firstPage());

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verifyNoInteractions(repository);
    }

    // --- helpers -------------------------------------------------------------

    private static Page<AuditRecord> pageOf(AuditRecord... rows) {
        List<AuditRecord> content = List.of(rows);
        return new PageImpl<>(content, PageRequest.of(0, 20), content.size());
    }

    private static AuditRecord auditRow(UUID societyId) {
        AuditRecord row = new AuditRecord(
                societyId, UUID.randomUUID(), "ACTION", "Target", "t-1", null, null);
        // id/occurredAt are DB-assigned; set them here so the view mapping is exercised.
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(row, "occurredAt", OffsetDateTime.now(ZoneOffset.UTC));
        return row;
    }
}
