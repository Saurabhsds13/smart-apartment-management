package com.societix.audit.api;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-side view model for a stored audit record returned by
 * {@link AuditService#query(AuditQuery)} (task 11.1; design.md "Audit (audit)"; Req 16.5).
 *
 * <p>This is the public, cross-module projection of an {@code audit_records} row — the internal
 * JPA entity never crosses the module boundary. It exposes the attribution captured at write time:
 * the owning Society (absent for platform-level events, Req 13.5/16.5), the acting user, the action
 * and target, the before/after summaries (compact JSON strings), and the event instant.
 *
 * @param id            the audit record's stable identifier
 * @param societyId     the owning Society id, or {@code null} for a platform-level event
 * @param actorUserId   the acting user id, or {@code null} when no user was resolved
 * @param action        the recorded action code
 * @param targetType    the kind of entity acted upon
 * @param targetId      the target entity identifier as text, or {@code null}
 * @param beforeSummary the compact-JSON before-summary, or {@code null}
 * @param afterSummary  the compact-JSON after-summary, or {@code null}
 * @param occurredAt    the event instant (UTC; display in IST at presentation, Req 16.1)
 */
public record AuditRecordView(
        UUID id,
        UUID societyId,
        UUID actorUserId,
        String action,
        String targetType,
        String targetId,
        String beforeSummary,
        String afterSummary,
        OffsetDateTime occurredAt) {
}
