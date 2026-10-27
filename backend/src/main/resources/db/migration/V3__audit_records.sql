-- =============================================================================
-- V3__audit_records.sql — the `audit_records` table (immutable audit trail)
-- =============================================================================
--
-- Purpose (task 11.1; design.md "Data Models > Table-by-table" > audit_records, ADR-6
-- "Audit Immutability", Req 16.1-16.6, 13.5, BR6):
--   Creates the append-only `audit_records` table that backs the Audit_Service. Every
--   financial, security, and administrative state change writes exactly one row here,
--   atomically within the originating service transaction (Req 16.1-16.3, 16.6) — the
--   AuditService.record(...) call participates in the caller's transaction, so the state
--   change and its audit record commit or roll back together. There is no @Async and no
--   separate transaction.
--
-- Platform-level vs tenant-owned:
--   `audit_records` is a documented exception to the "every tenant-owned table carries a
--   NOT NULL society_id" convention (design "Conventions": "Platform-level tables
--   (societies, users, platform audit) are the only exceptions"). `society_id` is NULLABLE
--   here because platform-level events (e.g. a Platform_Admin acting at platform scope with
--   no single owning society, Req 13.5/16.5) must also be auditable. Tenant-scoped audit
--   queries filter on society_id = current tenant; platform-scoped Platform_Admin review is
--   the only path that reads across societies (Req 16.5).
--
-- Columns (design "audit_records — id PK; society_id (nullable only for platform-level
--   events, Req 13.5); actor_user_id; action; target_type; target_id; before_summary JSONB;
--   after_summary JSONB; occurred_at TIMESTAMPTZ (UTC; displayed IST, Req 16.1)"):
--   * id             — UUID PK, defaults to uuid_generate_v7() (V1 baseline; v7 for locality).
--   * society_id     — owning society (NULL only for platform-level events). No FK-driven
--                      cascade concerns: audit rows are never deleted (see immutability below),
--                      so the reference is modeled as a plain FK to societies(id).
--   * actor_user_id  — the acting user (NULL permitted for pre-authentication/system events
--                      such as a failed login where no user is yet resolved). No FK to users:
--                      the users table is owned by the identity module's later migration and
--                      audit must never block on, or be deleted by, identity-side changes.
--   * action         — short action code (e.g. 'PAYMENT_RECORDED', 'ROLE_ASSIGNED'); NOT NULL.
--   * target_type    — the kind of entity acted upon (e.g. 'Invoice', 'Booking'); NOT NULL.
--   * target_id      — identifier of the target entity as text (UUIDs, composite keys, or
--                      natural keys all serialize uniformly); nullable for actions with no
--                      single target.
--   * before_summary — JSONB snapshot of relevant state BEFORE the change (nullable; e.g. a
--                      create has no before-state). Summary only — never secrets/PII in full.
--   * after_summary  — JSONB snapshot of relevant state AFTER the change (nullable).
--   * occurred_at    — event instant, TIMESTAMPTZ stored UTC (Req 23.1), displayed in IST at
--                      presentation time (Req 16.1). NOT NULL default now().
--
-- Index:
--   * ix_audit_records_society_occurred (society_id, occurred_at) — tenant-first index that
--     supports the common query "a society's audit records, most-recent first" (design IX
--     "(society_id, occurred_at)").
--
-- -----------------------------------------------------------------------------
-- IMMUTABILITY — append-only, enforced at the DATABASE layer (Req 16.4, BR6)
-- -----------------------------------------------------------------------------
--   Requirement 16.4 / BR6: no application operation available to any user may update or
--   delete a stored audit record. The application enforces this by having NO update/delete
--   code path on audit_records anywhere (the AuditService exposes only record(...) and read
--   queries — no save-over, no delete). This migration adds the DATABASE safeguards that make
--   immutability a database-enforced guarantee rather than a convention (design ADR-6):
--
--     (a) A `BEFORE UPDATE OR DELETE` trigger that unconditionally raises an exception, so any
--         attempt to modify or remove a stored audit row — from any role, through any path
--         (app, ad-hoc SQL, a future buggy migration) — fails loudly. INSERT is untouched, so
--         the append-only write path keeps working. This is defined below and is effective
--         regardless of role privileges.
--
--     (b) An OPERATIONAL step, documented in infra/README.md and consistent with the existing
--         two-role model (migration/owner role vs. application runtime role): the application
--         RUNTIME role's UPDATE and DELETE privileges on audit_records are REVOKEd so the
--         running application is granted INSERT/SELECT only. REVOKE is NOT performed in this
--         migration because the runtime role is created operationally (outside app migrations;
--         role creation needs privileges the runtime role must not hold — see V1 baseline and
--         infra/README.md). The trigger in (a) is the in-migration, role-independent backstop;
--         the REVOKE is the least-privilege complement applied when provisioning the runtime
--         role. Example (run operationally, after this migration, by the owner/DBA):
--             REVOKE UPDATE, DELETE ON audit_records FROM societix_app;
-- =============================================================================


-- -----------------------------------------------------------------------------
-- audit_records
-- -----------------------------------------------------------------------------
CREATE TABLE audit_records (
    id             UUID         NOT NULL DEFAULT uuid_generate_v7(),
    society_id     UUID         NULL,
    actor_user_id  UUID         NULL,
    action         TEXT         NOT NULL,
    target_type    TEXT         NOT NULL,
    target_id      TEXT         NULL,
    before_summary JSONB        NULL,
    after_summary  JSONB        NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_audit_records PRIMARY KEY (id),
    -- society_id, when present, must reference a real society. NULL is permitted for
    -- platform-level events (Req 13.5/16.5). No ON DELETE action: audit rows are never
    -- deleted and societies are never hard-deleted (suspension is a status flip, V2).
    CONSTRAINT fk_audit_records_society FOREIGN KEY (society_id) REFERENCES societies(id),
    -- Action and target_type are the minimum attribution required by Req 16.1-16.3.
    CONSTRAINT ck_audit_records_action_not_blank      CHECK (length(btrim(action)) > 0),
    CONSTRAINT ck_audit_records_target_type_not_blank CHECK (length(btrim(target_type)) > 0)
);

COMMENT ON TABLE audit_records IS
    'Append-only immutable audit trail (Req 16, BR6). One row per financial/security/'
    'administrative state change, written atomically within the originating transaction. '
    'society_id is NULL only for platform-level events (Req 13.5/16.5). UPDATE/DELETE are '
    'blocked by trg_audit_records_immutable; the runtime role is additionally REVOKEd '
    'UPDATE/DELETE operationally (see infra/README.md).';
COMMENT ON COLUMN audit_records.society_id IS
    'Owning society; NULL only for platform-level events (Req 13.5/16.5).';
COMMENT ON COLUMN audit_records.occurred_at IS
    'Event instant, TIMESTAMPTZ stored UTC (Req 23.1); displayed in IST at presentation (Req 16.1).';


-- -----------------------------------------------------------------------------
-- Tenant-first index: a society's audit records, most-recent first.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_audit_records_society_occurred
    ON audit_records (society_id, occurred_at);


-- -----------------------------------------------------------------------------
-- DB-enforced immutability: block every UPDATE and DELETE (Req 16.4, BR6)
-- -----------------------------------------------------------------------------
-- Role-independent backstop. INSERT is deliberately NOT guarded so the append-only write
-- path (AuditService.record) continues to work; only modification and removal of a stored
-- row are rejected. Raising here makes any offending statement fail its whole transaction.
CREATE OR REPLACE FUNCTION audit_records_block_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'audit_records is append-only: % is not permitted on a stored audit record (Req 16.4, BR6)',
        TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

COMMENT ON FUNCTION audit_records_block_mutation() IS
    'BEFORE UPDATE OR DELETE guard for audit_records: unconditionally raises to enforce '
    'append-only immutability (Req 16.4, BR6). INSERT is not guarded.';

CREATE TRIGGER trg_audit_records_immutable
    BEFORE UPDATE OR DELETE ON audit_records
    FOR EACH ROW
    EXECUTE FUNCTION audit_records_block_mutation();
