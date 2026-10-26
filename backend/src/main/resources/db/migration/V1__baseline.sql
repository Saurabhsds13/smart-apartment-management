-- =============================================================================
-- V1__baseline.sql — Societix platform schema baseline
-- =============================================================================
--
-- Purpose (task 1.3; design.md "Data Models > Conventions", ADR-2, Req A1/A3/1.1):
--   This baseline migration establishes DATABASE-WIDE CONVENTIONS and extensions
--   that every later migration relies on. It deliberately creates NO domain tables:
--   `societies` arrives in migration V2 (task 7.1), `users` in a later migration
--   (task 9.1), and so on. Flyway is the single source of truth for schema; Hibernate
--   `ddl-auto` is disabled in every non-test profile (see application.yml).
--
-- What this migration sets up:
--   1. Required PostgreSQL extensions for UUID primary keys and crypto helpers.
--   2. A canonical UUIDv7 generator function used as the default for UUID PKs
--      (v7 is preferred for index locality — design "Conventions").
--   3. A shared `set_updated_at()` trigger function reused by mutable tables to keep
--      `updated_at` current (TIMESTAMPTZ in UTC).
--   4. The database default time zone pinned to UTC (Req 23.1).
--
-- =============================================================================
-- RUNTIME DATABASE ROLE — SECURITY POSTURE (read before deploying)
-- =============================================================================
--
--   The role the application connects as AT RUNTIME MUST be a NON-SUPERUSER and MUST
--   NOT have the BYPASSRLS attribute.
--
--   Why this matters (design.md ADR-2 "defense-in-depth", Req 1.5, 1.14):
--     Tenant isolation has two independent layers. The primary layer is application-side
--     scoping (Hibernate society filter + base repository predicate). The defense-in-depth
--     layer is PostgreSQL Row-Level Security: tenant-owned tables get
--     `ENABLE/FORCE ROW LEVEL SECURITY` and a `tenant_isolation` policy comparing
--     `society_id` to the per-transaction GUC `app.current_society` (added in task 8.2).
--
--     A PostgreSQL SUPERUSER, a table OWNER (unless FORCE RLS is set), or any role with
--     BYPASSRLS SILENTLY IGNORES row-level security policies. If the application connected
--     as such a role, the RLS backstop would be dead weight and a single missed predicate
--     in application code would become a silent cross-tenant data leak. Therefore:
--
--       - A dedicated MIGRATION-TIME role (which may own objects / have DDL rights) runs
--         Flyway and OWNS these tables. It is NOT the role the application uses to serve
--         traffic.
--       - The RUNTIME role is granted only DML (SELECT/INSERT/UPDATE/DELETE as needed per
--         table), is NOT a superuser, does NOT own the tenant-owned tables, and does NOT
--         have BYPASSRLS. `FORCE ROW LEVEL SECURITY` (set per table in task 8.2) additionally
--         ensures even a table owner is subject to policies.
--       - Provisioning these two roles is an operational/infra concern (see infra/README.md
--         and the deployment manifests), not something this migration performs, because
--         creating roles requires privileges the runtime role must not hold.
--
--   This migration itself only sets up extensions and shared helpers; it does not enable
--   RLS (that is table-by-table in task 8.2, once tenant-owned tables exist).
--
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Extensions
-- -----------------------------------------------------------------------------
-- pgcrypto provides gen_random_uuid() (UUIDv4), gen_random_bytes() and digest()/crypt()
-- helpers. It is the modern, in-core choice and underpins the UUIDv7 generator below.
-- (The older uuid-ossp extension is intentionally NOT used: pgcrypto covers our needs and
-- ships with core PostgreSQL.)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- btree_gist is required later (task 29.2) for the bookings exclusion constraint that
-- mixes equality (facility_id) with a range-overlap operator (tstzrange &&) in a single
-- GiST index. Creating it here keeps the extension set centralized in the baseline.
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- -----------------------------------------------------------------------------
-- 2. UUIDv7 generator
-- -----------------------------------------------------------------------------
-- UUID primary keys are the convention (design "Conventions"), with v7 PREFERRED for
-- index locality: v7 embeds a Unix-millisecond timestamp in the high bits, so newly
-- generated keys are time-ordered and insert into B-tree indexes with far less page
-- fragmentation than random v4 keys.
--
-- PostgreSQL's built-in uuidv7() only exists on PostgreSQL 18+. To work on the design's
-- PostgreSQL 16+ baseline, we define a portable generator built on pgcrypto. It assembles
-- an RFC 9562-compliant v7 UUID textually, then casts to uuid:
--   * bytes 0-5 : 48-bit big-endian millisecond Unix timestamp (12 hex digits)
--   * byte  6   : high nibble = version (0x7), low nibble random
--   * byte  8   : top two bits = variant (0b10 -> hex 8/9/a/b), remainder random
--   * remaining : random
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $$
    SELECT (
        -- 48-bit millisecond timestamp -> 12 hex digits
        lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0')
        -- version nibble 7, then 3 random hex digits (completes bytes 6-7)
        || '7' || substr(encode(gen_random_bytes(2), 'hex'), 1, 3)
        -- variant nibble (one of 8,9,a,b), then 3 random hex digits (completes bytes 8-9)
        || substr('89ab', (get_byte(gen_random_bytes(1), 0) % 4) + 1, 1)
        || substr(encode(gen_random_bytes(2), 'hex'), 1, 3)
        -- final 48 random bits -> 12 hex digits (bytes 10-15)
        || encode(gen_random_bytes(6), 'hex')
    )::uuid;
$$;

COMMENT ON FUNCTION uuid_generate_v7() IS
    'RFC 9562 UUIDv7 generator (time-ordered). Preferred default for UUID primary keys '
    'for index locality. Portable to PostgreSQL 16/17; PostgreSQL 18+ callers may use '
    'the built-in uuidv7() instead.';


-- -----------------------------------------------------------------------------
-- 3. Shared updated_at trigger function
-- -----------------------------------------------------------------------------
-- Convention: mutable tables carry created_at/updated_at TIMESTAMPTZ (UTC). Rather than
-- repeating a trigger body per table, later migrations attach this one function as a
-- BEFORE UPDATE trigger. now() returns a TIMESTAMPTZ; storage is UTC per the datasource/
-- Hibernate time_zone=UTC setting, and this keeps updated_at authoritative server-side.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION set_updated_at() IS
    'BEFORE UPDATE trigger function: stamps updated_at with now() (TIMESTAMPTZ, UTC). '
    'Attached per mutable table in later migrations.';


-- -----------------------------------------------------------------------------
-- 4. Session time zone
-- -----------------------------------------------------------------------------
-- All timestamps are TIMESTAMPTZ stored in UTC (Req 23.1); display conversion to IST is a
-- presentation concern. Pinning the database default time zone to UTC makes server-side
-- now()/clock_timestamp() and any casual timestamp display unambiguous. This affects only
-- this database, not the whole cluster. current_database() keeps it environment-agnostic
-- (works for the local, test, and prod database names alike).
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'UTC');
END;
$$;
