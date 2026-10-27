-- =============================================================================
-- V2__societies.sql — the `societies` table (tenant root)
-- =============================================================================
--
-- Purpose (task 7.1; design.md "Data Models > Table-by-table" > societies, Req 13.1,
-- 13.3, 1.1, BR9):
--   Creates the platform-level `societies` table — the tenant root of the system.
--   Every tenant-owned table in later migrations carries
--   `society_id UUID NOT NULL REFERENCES societies(id)` (design "Conventions", Req 1.1);
--   this migration establishes the table those foreign keys point at.
--
-- Platform-level, NOT tenant-owned:
--   `societies` is one of the documented exceptions to the tenant-ownership rule
--   (design "Conventions": "Platform-level tables (societies, users, platform audit) are
--   the only exceptions"). It is the tenant root itself, so it deliberately does NOT carry
--   a `society_id` column and is NOT subject to the `tenant_isolation` RLS policy added for
--   tenant-owned tables in task 8.2.
--
-- Columns (design "societies (platform-level) — id PK; name; status (active/suspended);
--   created_at/updated_at. Suspension flips status; data retained."):
--   * id         — UUID PK, defaults to uuid_generate_v7() (V1 baseline; v7 for index locality).
--   * name       — the society's display name; NOT NULL, non-empty.
--   * status     — lifecycle state, constrained to 'active' / 'suspended' (Req 13.1, 13.3, BR9).
--                  Suspension is a status flip only; society data is retained, never deleted.
--   * created_at — row creation instant, TIMESTAMPTZ (UTC), NOT NULL default now().
--   * updated_at — last-modification instant, TIMESTAMPTZ (UTC), kept current by the shared
--                  set_updated_at() BEFORE UPDATE trigger from the V1 baseline.
--
-- No optimistic-locking `version` column: the design reserves `@Version`/`version BIGINT`
-- for tables with concurrent updates (invoices, bookings, account credits, maintenance
-- requests). `societies` is low-write (provision/suspend) and does not need it.
-- Provisioning and suspension *logic* arrive in tasks 32.1/32.2; this migration only
-- models the table and its status field.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- societies
-- -----------------------------------------------------------------------------
CREATE TABLE societies (
    id         UUID         NOT NULL DEFAULT uuid_generate_v7(),
    name       TEXT         NOT NULL,
    status     TEXT         NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_societies PRIMARY KEY (id),
    -- Status is a closed set; enforce it at the DB layer so no path (app or SQL) can store
    -- an out-of-range lifecycle state (Req 13.1, 13.3, BR9).
    CONSTRAINT ck_societies_status CHECK (status IN ('active', 'suspended')),
    -- Name must be present and non-blank (trim to reject whitespace-only names).
    CONSTRAINT ck_societies_name_not_blank CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE societies IS
    'Platform-level tenant root. One row per housing society. NOT tenant-owned: carries no '
    'society_id and is exempt from tenant_isolation RLS. Suspension flips status; data retained.';
COMMENT ON COLUMN societies.status IS
    'Lifecycle state: active | suspended (Req 13.1/13.3/BR9). Suspension retains all society data.';


-- -----------------------------------------------------------------------------
-- updated_at trigger (shared set_updated_at() function from V1__baseline.sql)
-- -----------------------------------------------------------------------------
-- Keeps updated_at authoritative server-side on every UPDATE, regardless of the caller.
CREATE TRIGGER trg_societies_set_updated_at
    BEFORE UPDATE ON societies
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
