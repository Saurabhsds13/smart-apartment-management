package com.societix.audit.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link AuditRecord} (task 11.1).
 *
 * <p>Lives in {@code audit.internal}: it is the audit module's internal persistence, not its public
 * contract (only {@code com.societix.audit.api} crosses the module boundary; ArchUnit enforces this).
 *
 * <p><strong>Append-only surface.</strong> Although {@link JpaRepository} declares {@code save} and
 * {@code delete} methods, the audit module's own code only ever inserts (via {@code save} of a new,
 * write-once {@link AuditRecord}) and reads. No caller updates or deletes audit rows; the database
 * trigger {@code trg_audit_records_immutable} and the least-privilege runtime role are the enforced
 * backstop for Req 16.4/BR6.
 *
 * <p><strong>Tenant scoping is explicit here.</strong> {@code audit_records} is not a tenant-owned
 * table in the usual sense ({@code society_id} is nullable for platform-level events, Req 13.5), so
 * this repository is not routed through the central tenant-scoping base repository. Instead the
 * service supplies the society predicate from {@code TenantContext}: {@link #findBySocietyId} for a
 * tenant reviewer and {@link #findAllBy} only for a Platform_Admin at platform scope (Req 16.5).
 */
public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    /**
     * Returns a page of audit records for a single Society, ordered by the supplied {@link Pageable}
     * (the service requests newest-first). Used for tenant-scoped review (Req 16.5): only rows whose
     * {@code society_id} matches the reviewer's resolved tenant are returned, so platform-level rows
     * ({@code society_id IS NULL}) and other tenants' rows are never visible to a tenant reviewer.
     *
     * @param societyId the reviewer's resolved Society id
     * @param pageable  paging and sort
     * @return the matching page of audit records
     */
    Page<AuditRecord> findBySocietyId(UUID societyId, Pageable pageable);

    /**
     * Returns a page of all audit records, ordered by the supplied {@link Pageable}. This
     * platform-wide read is reserved for a Platform_Admin acting at platform scope — the sole
     * exception to tenant scoping (Req 16.5). The service gates access to this method on
     * {@code TenantContext.isPlatformAdmin()}.
     *
     * @param pageable paging and sort
     * @return a page of audit records across all societies (including platform-level rows)
     */
    Page<AuditRecord> findAllBy(Pageable pageable);
}
