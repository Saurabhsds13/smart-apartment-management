-- =============================================================================
-- V5__rls_policies.sql — PostgreSQL Row-Level Security (defense-in-depth)
-- =============================================================================
--
-- Purpose (task 8.2; design.md ADR-2 "tenant isolation — defense-in-depth",
-- Req 1.5, 1.14; coordinates with task 8.1's RlsGucTenantScope):
--   Enables PostgreSQL Row-Level Security (RLS) on TENANT-OWNED tables as the SECOND,
--   database-enforced isolation layer. The PRIMARY layer is application-side scoping
--   (the Hibernate `societyFilter` + base-repository predicate, task 7.3). RLS is the
--   backstop: if a single tenant predicate is ever missed in application code, the
--   database itself denies the cross-tenant row rather than leaking it.
--
--   The policy predicate compares each row's `society_id` against the per-transaction
--   session GUC `app.current_society`. That GUC is set by the application at transaction
--   start via `SET LOCAL app.current_society = '<uuid>'` (task 8.1, RlsGucTenantScope) on
--   the same connection that runs the transaction's queries, and is auto-cleared by
--   PostgreSQL on commit/rollback (SET LOCAL is transaction-scoped), with a belt-and-braces
--   `RESET app.current_society` on pool return (Req 1.10). The application and the RLS
--   policy therefore observe the SAME server-derived society.
--
-- =============================================================================
-- FAIL-CLOSED WHEN THE GUC IS UNSET (verified behavior — Req 1.6)
-- =============================================================================
--   The predicate uses the two-argument form `current_setting('app.current_society', true)`.
--   The second argument `missing_ok => true` makes current_setting return NULL (instead of
--   raising "unrecognized configuration parameter") when the GUC was never set on the
--   connection — e.g. an operation that ran WITHOUT a resolved Tenant_Context.
--
--   When the GUC is unset, the predicate evaluates to:
--
--       society_id = NULL::uuid
--
--   In SQL three-valued logic, any comparison with NULL yields NULL (not TRUE), and a row
--   is visible under RLS only when the USING predicate is TRUE. NULL is therefore treated
--   as "does not match", so ZERO rows are visible and ZERO rows are writable. This is
--   exactly the desired FAIL-CLOSED behavior: an unresolved Tenant_Context yields no rows
--   at the database layer (Req 1.6), independently of the application-layer abort.
--
--   (Note: `society_id` is declared NOT NULL on every tenant-owned table, so the left-hand
--   side is never itself NULL; only an unset GUC can make the predicate NULL.)
--
-- =============================================================================
-- PLATFORM_ADMIN CROSS-SOCIETY ACCESS IS AUDITED, NOT A SILENT BYPASS (Req 1.14)
-- =============================================================================
--   Req 1.14: a Platform_Admin may access another Society's data ONLY through an explicit
--   platform-administration operation that writes an Audit_Record (actor, accessed society,
--   operation, timestamp) BEFORE the access completes. RLS must NOT be a loophole around
--   this. Concretely:
--
--     * The application RUNTIME role is NON-SUPERUSER and is NOT granted BYPASSRLS (see
--       V1__baseline.sql and infra/README.md). It is therefore ALWAYS subject to these
--       policies — there is no "admin connection" that silently sees all societies.
--
--     * A Platform_Admin drill-down into one Society's tenant-owned data runs through the
--       SAME GUC mechanism as any tenant request: the audited platform-admin operation
--       (task 13.x / Req 13.7) first records the Audit_Record, then sets
--       `app.current_society` to the ONE target society for the duration of that single
--       audited transaction, so even the admin path sees exactly one society at a time and
--       every cross-society access leaves an audit trail. The admin never gets an
--       unconstrained, all-societies view through the runtime role.
--
--     * `FORCE ROW LEVEL SECURITY` (set per table below) additionally ensures that even the
--       table OWNER (the migration/owner role) is subject to these policies, closing the
--       "owner bypasses RLS" gap. No application path is exempt.
--
--     * Any genuinely cross-society maintenance (out of band, by a DBA) is an operational,
--       audited action performed with the owner/superuser — never the application runtime
--       role. We deliberately do NOT create a BYPASSRLS application role.
--
-- =============================================================================
-- CONVENTION FOR EVERY FUTURE TENANT-OWNED TABLE (M3+) — MANDATORY
-- =============================================================================
--   Scope of THIS migration: the only tenant-owned table that exists so far is
--   `user_society_memberships` (V4). The platform-level tables `societies` (V2), `users`
--   (V4) and `audit_records` (V3, society_id nullable) are documented exceptions to the
--   tenant-ownership rule and MUST NOT receive the `tenant_isolation` policy — they carry no
--   tenant-owned semantics keyed on a NOT NULL society_id. Only `user_society_memberships`
--   is enabled below.
--
--   CONVENTION: EVERY future tenant-owned table (buildings, flats, resident_associations,
--   invoices, invoice_lines, receipts, payments, bookings, visitor_records, notifications,
--   maintenance_requests, …; M3 onward) MUST, in its OWN migration, repeat the same three
--   statements this migration applies to user_society_memberships:
--
--       ALTER TABLE <t> ENABLE  ROW LEVEL SECURITY;
--       ALTER TABLE <t> FORCE   ROW LEVEL SECURITY;
--       CREATE POLICY tenant_isolation ON <t>
--           USING      (society_id = current_setting('app.current_society', true)::uuid)
--           WITH CHECK (society_id = current_setting('app.current_society', true)::uuid);
--
--   Rationale for the shape:
--     * ENABLE turns RLS on; without it the policy is inert.
--     * FORCE subjects even the table owner to the policy (so the owner can't silently bypass).
--     * USING constrains which existing rows are VISIBLE to reads (SELECT) and to the row-
--       selection half of UPDATE/DELETE.
--     * WITH CHECK constrains which rows may be WRITTEN by INSERT and the post-image of
--       UPDATE, so an actor cannot insert or relabel a row into a society other than its
--       Tenant_Context. Using the SAME predicate for both halves means reads and writes are
--       constrained identically — a row is readable iff it belongs to the current society,
--       and writable iff it would belong to the current society.
--
--   This per-table repetition is deliberate (not a single schema-wide switch): RLS attaches
--   to concrete tables, and making each migration opt in keeps the backstop auditable and
--   impossible to forget silently — a tenant-owned table with no policy would be an obvious
--   review miss. The human-readable version of this convention also lives in infra/README.md
--   ("Database roles and Row-Level Security"); keep the two in sync.
--
--   There is intentionally NO generic trigger/event-trigger that auto-applies RLS: an event
--   trigger that guessed "has a society_id column ⇒ tenant-owned" would wrongly rope in the
--   platform-level exceptions (audit_records has a nullable society_id) and would hide the
--   decision from migration review. The explicit three-line block per table is the contract.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- user_society_memberships (tenant-owned, created in V4) — enable RLS backstop
-- -----------------------------------------------------------------------------
-- Turn RLS on and FORCE it so even the table owner is subject to the policy (defense against
-- an owner/BYPASSRLS escape — see the header note on Platform_Admin / Req 1.14).
ALTER TABLE user_society_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_society_memberships FORCE  ROW LEVEL SECURITY;

-- tenant_isolation: a membership row is visible/writable only when its society_id equals the
-- per-transaction GUC app.current_society (set by task 8.1's RlsGucTenantScope). USING guards
-- reads and the row-selection side of UPDATE/DELETE; WITH CHECK guards INSERT and the post-image
-- of UPDATE, so writes cannot escape the current Tenant_Context either. An unset GUC makes the
-- predicate NULL ⇒ zero rows (fail-closed, Req 1.6 — see header). The second arg `true`
-- (missing_ok) is what lets an unset GUC return NULL instead of raising.
CREATE POLICY tenant_isolation ON user_society_memberships
    USING      (society_id = current_setting('app.current_society', true)::uuid)
    WITH CHECK (society_id = current_setting('app.current_society', true)::uuid);

COMMENT ON POLICY tenant_isolation ON user_society_memberships IS
    'Defense-in-depth tenant isolation (ADR-2; Req 1.5): row visible/writable only when '
    'society_id = current_setting(''app.current_society'', true)::uuid (the per-transaction GUC '
    'set by RlsGucTenantScope, task 8.1). Unset GUC => NULL predicate => zero rows (fail-closed, '
    'Req 1.6). FORCE RLS subjects even the owner; the runtime role is non-superuser/NOBYPASSRLS. '
    'Platform_Admin cross-society access is audited and GUC-scoped, never a silent bypass (Req 1.14). '
    'CONVENTION: every future tenant-owned table repeats ENABLE+FORCE RLS + this policy in its own migration.';
