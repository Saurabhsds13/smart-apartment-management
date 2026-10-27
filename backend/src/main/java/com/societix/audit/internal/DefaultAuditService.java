package com.societix.audit.internal;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditQuery;
import com.societix.audit.api.AuditRecordView;
import com.societix.audit.api.AuditService;
import com.societix.common.api.PageResponse;
import com.societix.tenancy.api.TenantContext;

/**
 * Default {@link AuditService} implementation (task 11.1; design.md "Audit (audit)", ADR-6;
 * Req 16.1-16.6, BR6).
 *
 * <p><strong>Write path (Req 16.1-16.3, 16.6).</strong> {@link #record(AuditEntry)} is annotated
 * {@link Propagation#MANDATORY}: it must join an already-open transaction — the one the originating
 * service uses for its state change — and never starts its own. This is what makes the state change
 * and its audit record atomic: they share a transaction and therefore commit or roll back together.
 * If no transaction is active, Spring throws
 * {@link org.springframework.transaction.IllegalTransactionStateException} rather than letting a
 * non-atomic audit write slip through. There is no {@code @Async} anywhere on this path.
 *
 * <p><strong>Read path (Req 16.5).</strong> {@link #query(AuditQuery)} resolves tenant scope
 * server-side from {@link TenantContext}: a Platform_Admin at platform scope reads platform-wide; a
 * tenant reviewer reads only their own Society's rows; an unresolved non-platform context yields an
 * empty page (Req 1.6) rather than another tenant's data. Scope is never taken from client input.
 */
@Service
public class DefaultAuditService implements AuditService {

    /** Audit records are reviewed newest-first; occurred_at is the stable ordering key. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AuditRecordRepository repository;
    private final TenantContext tenantContext;

    /**
     * @param repository    the append-only audit-record persistence
     * @param tenantContext request-scoped tenant scope resolved from the authenticated session
     */
    public DefaultAuditService(AuditRecordRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        AuditRecord row = new AuditRecord(
                entry.societyId(),
                entry.actorUserId(),
                entry.action(),
                entry.targetType(),
                entry.targetId(),
                entry.beforeSummary(),
                entry.afterSummary());
        repository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditRecordView> query(AuditQuery query) {
        Objects.requireNonNull(query, "query");
        PageRequest pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);

        Page<AuditRecord> page;
        if (tenantContext.isPlatformAdmin()) {
            // Platform scope: the sole exception to tenant scoping (Req 16.5).
            page = repository.findAllBy(pageable);
        } else if (tenantContext.isResolved()) {
            // Tenant reviewer: only their own Society's records (Req 16.5).
            page = repository.findBySocietyId(tenantContext.currentSocietyId(), pageable);
        } else {
            // Unresolved, non-platform context: never leak another tenant's data (Req 1.6).
            return PageResponse.empty(query.size());
        }

        List<AuditRecordView> content = page.getContent().stream()
                .map(DefaultAuditService::toView)
                .toList();
        return PageResponse.of(content, query.page(), query.size(), page.getTotalElements());
    }

    private static AuditRecordView toView(AuditRecord r) {
        return new AuditRecordView(
                r.getId(),
                r.getSocietyId(),
                r.getActorUserId(),
                r.getAction(),
                r.getTargetType(),
                r.getTargetId(),
                r.getBeforeSummary(),
                r.getAfterSummary(),
                r.getOccurredAt());
    }
}
