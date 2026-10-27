package com.societix.audit.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable value object describing a single audit event to be recorded by the
 * {@link AuditService} (task 11.1; design.md "Audit (audit)", ADR-6; Req 16.1-16.3).
 *
 * <p>An {@code AuditEntry} is the caller's contract for "what happened": who acted, what action,
 * on which target, and an optional before/after summary of the affected state. The
 * {@code occurred_at} timestamp and the generated identifier are assigned by the service/database
 * on write (UTC, Req 23.1; displayed in IST at presentation, Req 16.1) — callers do not supply
 * them, so the clock stays server-authoritative.
 *
 * <h2>Tenant scope</h2>
 * {@link #societyId()} is the owning Society for the event. It is {@link Optional#empty() empty}
 * <strong>only</strong> for platform-level events — e.g. a Platform_Admin acting at platform scope
 * with no single owning Society (Req 13.5, 16.5). Every tenant-scoped state change must supply a
 * society id so the record is visible to that tenant's authorized reviewers and to no other
 * (Req 16.5).
 *
 * <h2>Summaries</h2>
 * {@link #beforeSummary()} and {@link #afterSummary()} are optional JSON documents (serialized as
 * compact JSON strings) capturing the relevant state before and after the change. They are
 * <em>summaries</em>: include only the fields needed to review the change, never secrets or full
 * PII (consistent with Req 21.4). A create typically has no before-summary; a delete typically has
 * no after-summary.
 *
 * <p>Construct instances with {@link #builder()}. Instances are immutable and safe to share.
 *
 * @param societyId     the owning Society id, or {@code null} for a platform-level event
 * @param actorUserId   the acting user id, or {@code null} when no user is resolved (e.g. a
 *                      pre-authentication system event)
 * @param action        a short, stable action code (e.g. {@code "PAYMENT_RECORDED"}); required,
 *                      non-blank
 * @param targetType    the kind of entity acted upon (e.g. {@code "Invoice"}); required, non-blank
 * @param targetId      the target entity identifier as text, or {@code null} when the action has
 *                      no single target
 * @param beforeSummary a compact JSON string summarizing state before the change, or {@code null}
 * @param afterSummary  a compact JSON string summarizing state after the change, or {@code null}
 */
public record AuditEntry(
        UUID societyId,
        UUID actorUserId,
        String action,
        String targetType,
        String targetId,
        String beforeSummary,
        String afterSummary) {

    /**
     * Canonical constructor validating the always-required attribution fields. {@code action} and
     * {@code targetType} must be present and non-blank (Req 16.1-16.3); all other fields are
     * optional per the field documentation above.
     */
    public AuditEntry {
        action = requireNonBlank(action, "action");
        targetType = requireNonBlank(targetType, "targetType");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /**
     * The owning Society for this event, or {@link Optional#empty()} for a platform-level event
     * with no single owning Society (Req 13.5, 16.5).
     *
     * @return the owning Society id, if any
     */
    public Optional<UUID> societyIdOptional() {
        return Optional.ofNullable(societyId);
    }

    /**
     * The acting user, or {@link Optional#empty()} when no user was resolved for the event.
     *
     * @return the acting user id, if any
     */
    public Optional<UUID> actorUserIdOptional() {
        return Optional.ofNullable(actorUserId);
    }

    /**
     * Starts building an {@code AuditEntry}.
     *
     * @return a new, empty {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link AuditEntry}. Required fields ({@code action}, {@code targetType})
     * are validated by the record's canonical constructor on {@link #build()}.
     */
    public static final class Builder {
        private UUID societyId;
        private UUID actorUserId;
        private String action;
        private String targetType;
        private String targetId;
        private String beforeSummary;
        private String afterSummary;

        private Builder() {
        }

        /** Sets the owning Society id ({@code null} for a platform-level event). */
        public Builder societyId(UUID societyId) {
            this.societyId = societyId;
            return this;
        }

        /** Sets the acting user id ({@code null} when no user is resolved). */
        public Builder actorUserId(UUID actorUserId) {
            this.actorUserId = actorUserId;
            return this;
        }

        /** Sets the action code (required, non-blank). */
        public Builder action(String action) {
            this.action = action;
            return this;
        }

        /** Sets the target entity type (required, non-blank). */
        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        /** Sets the target entity identifier as text ({@code null} when there is no single target). */
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        /** Sets the compact-JSON before-summary ({@code null} when not applicable). */
        public Builder beforeSummary(String beforeSummary) {
            this.beforeSummary = beforeSummary;
            return this;
        }

        /** Sets the compact-JSON after-summary ({@code null} when not applicable). */
        public Builder afterSummary(String afterSummary) {
            this.afterSummary = afterSummary;
            return this;
        }

        /**
         * Builds the immutable {@link AuditEntry}.
         *
         * @return the built entry
         * @throws IllegalArgumentException if {@code action} or {@code targetType} is blank
         */
        public AuditEntry build() {
            return new AuditEntry(
                    societyId,
                    actorUserId,
                    Objects.requireNonNull(action, "action"),
                    Objects.requireNonNull(targetType, "targetType"),
                    targetId,
                    beforeSummary,
                    afterSummary);
        }
    }
}
