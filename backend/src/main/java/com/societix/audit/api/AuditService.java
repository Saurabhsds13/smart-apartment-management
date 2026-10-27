package com.societix.audit.api;

import com.societix.common.api.PageResponse;

/**
 * Public contract of the <strong>audit</strong> module: records immutable audit entries and serves
 * tenant-scoped audit reviews (task 11.1; design.md "Audit (audit)", ADR-6; Req 16.1-16.6, BR6).
 *
 * <h2>Transactional, synchronous write (Req 16.1-16.3, 16.6)</h2>
 * {@link #record(AuditEntry)} runs <strong>inside the originating service's transaction</strong> —
 * it is neither {@code @Async} nor opened in a new transaction. The implementation joins the
 * caller's transaction (mandatory propagation), so the originating state change and its audit
 * record are atomic: they both commit or both roll back. A caller that performs a financial,
 * security, or administrative state change therefore invokes {@code record(...)} within the same
 * {@code @Transactional} service method as the change itself; if the audit write fails, the whole
 * transaction — including the state change — fails (Req 16.6). Because the write must participate
 * in an existing transaction, calling {@code record(...)} with no active transaction is a
 * programming error and is rejected.
 *
 * <h2>Append-only / immutable (Req 16.4, BR6)</h2>
 * This interface intentionally exposes <em>no</em> update or delete operation. There is no
 * application path to modify or remove a stored audit record; the database additionally enforces
 * append-only immutability via a trigger and a least-privilege runtime role (see
 * {@code V3__audit_records.sql} and {@code infra/README.md}).
 *
 * <h2>Tenant-scoped read (Req 16.5)</h2>
 * {@link #query(AuditQuery)} returns only the records within the caller's resolved
 * {@code TenantContext}. A Platform_Admin acting at platform scope is the sole exception and sees
 * platform-wide records. The scope is applied server-side from the authenticated session; it is
 * never a client parameter.
 */
public interface AuditService {

    /**
     * Records an audit entry within the caller's current transaction (Req 16.1-16.3, 16.6).
     *
     * <p>Must be invoked from a method already running in a transaction that also performs the
     * state change being audited, so the two commit atomically. The implementation uses mandatory
     * transaction propagation; if there is no active transaction this throws rather than silently
     * opening one, because a standalone audit write could not be atomic with any state change.
     *
     * @param entry the event to record (never {@code null}); {@code action} and {@code targetType}
     *              are required and the entry's society/actor/target/summaries follow
     *              {@link AuditEntry}
     * @throws org.springframework.transaction.IllegalTransactionStateException if invoked with no
     *         active transaction (mandatory propagation)
     */
    void record(AuditEntry entry);

    /**
     * Returns a page of audit records visible to the current actor, newest first (Req 16.5).
     *
     * <p>The result is scoped server-side to the caller's resolved tenant: a tenant reviewer sees
     * only their own Society's records; a Platform_Admin acting at platform scope sees platform-wide
     * records. When no tenant scope is resolved for a non-platform actor, the result is an empty
     * page (Req 1.6) rather than another tenant's data.
     *
     * @param query the pagination parameters (tenant scope is not a parameter — it is applied from
     *              the session)
     * @return a tenant-scoped, newest-first page of {@link AuditRecordView} (possibly empty)
     */
    PageResponse<AuditRecordView> query(AuditQuery query);
}
