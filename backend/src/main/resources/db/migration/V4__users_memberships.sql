-- =============================================================================
-- V4__users_memberships.sql — `users` and `user_society_memberships`
-- =============================================================================
--
-- Purpose (task 9.1; design.md "Data Models > Table-by-table" > users,
-- user_society_memberships; Req 2.1, 3.1, 3.6, 18.1, A2, RD8):
--   Creates the identity module's two foundational tables:
--     * users                    — platform-level account identity.
--     * user_society_memberships — a user's role(s) within one society (tenant-owned).
--   Password hashing/authentication logic arrives in later tasks (9.2, 10.x); this
--   migration only models `password_hash` as a plain, nullable column (a user may exist
--   before a password is set, e.g. invited-but-not-activated).
--
-- -----------------------------------------------------------------------------
-- DATA MINIMIZATION (RD8 / Req 18.1)
-- -----------------------------------------------------------------------------
--   `users` deliberately stores the MINIMUM identity attributes: email, a verification
--   flag, the password hash, a display name, and account-security state. There is NO
--   Aadhaar, NO PAN, NO date-of-birth, and NO government-ID / ID-document column — by
--   default the platform does not collect these (design RD8, Req 18.1). Any future need
--   for such data must be a deliberate, separately-reviewed decision, not an accident of
--   schema drift.
--
-- =============================================================================
-- TABLE 1 — users (platform-level, NOT tenant-owned)
-- =============================================================================
--
-- Platform-level, like `societies`:
--   `users` is one of the documented exceptions to the "every tenant-owned table carries a
--   NOT NULL society_id" convention (design "Conventions": platform-level tables —
--   societies, users, platform audit — are the only exceptions). A single user account is
--   global to the platform and may hold memberships in several societies, so the account
--   itself carries no `society_id` and is exempt from the `tenant_isolation` RLS policy.
--   The link between a user and a society is `user_society_memberships` below.
--
-- Columns (design "users — id PK; email UQ; email_verified bool (Req 2.14); password_hash
--   (adaptive hash, Req 2.4); full_name (RD8); status (active/locked/disabled);
--   failed_attempts, lockout_until (Req 2.7, 2.8); created_at/updated_at"):
--   * id              — UUID PK, defaults to uuid_generate_v7() (V1 baseline; v7 locality).
--   * email           — login identifier. Case-INSENSITIVE uniqueness is required (a user
--                       must not register 'A@x.com' and 'a@x.com' as two accounts). Rather
--                       than depend on the optional `citext` extension, email is TEXT and
--                       uniqueness is enforced by a UNIQUE index on lower(email) below.
--   * email_verified  — whether the address has been confirmed (Req 2.14). Default false;
--                       verification flow lands in task 10.3.
--   * password_hash   — salted adaptive one-way hash (Req 2.4). NULLABLE here: hashing and
--                       the set-password path are task 9.2; an account may exist before a
--                       password is set. Never stores plaintext.
--   * full_name       — display name (RD8). NOT NULL, non-blank.
--   * status          — account lifecycle, constrained to 'active'/'locked'/'disabled'
--                       (design users.status). Default 'active'.
--   * failed_attempts — consecutive failed-login counter (Req 2.7). Default 0, never < 0.
--   * lockout_until   — TIMESTAMPTZ until which login is locked, or NULL when not locked
--                       (Req 2.8). Lockout logic (threshold/window/progressive) is task 10.2;
--                       this migration only models the field.
--   * created_at/updated_at — TIMESTAMPTZ (UTC); updated_at kept current by set_updated_at().
-- =============================================================================
CREATE TABLE users (
    id              UUID         NOT NULL DEFAULT uuid_generate_v7(),
    email           TEXT         NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT false,
    password_hash   TEXT         NULL,
    full_name       TEXT         NOT NULL,
    status          TEXT         NOT NULL DEFAULT 'active',
    failed_attempts INTEGER      NOT NULL DEFAULT 0,
    lockout_until   TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    -- Status is a closed set; enforce at the DB so no path can store an out-of-range value.
    CONSTRAINT ck_users_status CHECK (status IN ('active', 'locked', 'disabled')),
    -- Email and full_name must be present and non-blank (trim rejects whitespace-only).
    CONSTRAINT ck_users_email_not_blank     CHECK (length(btrim(email)) > 0),
    CONSTRAINT ck_users_full_name_not_blank CHECK (length(btrim(full_name)) > 0),
    -- Failed-attempt counter is a non-negative count.
    CONSTRAINT ck_users_failed_attempts_non_negative CHECK (failed_attempts >= 0)
);

COMMENT ON TABLE users IS
    'Platform-level account identity (Req 2.1). NOT tenant-owned: carries no society_id and is '
    'exempt from tenant_isolation RLS; the user<->society link is user_society_memberships. Data '
    'minimization (RD8/Req 18.1): no Aadhaar/PAN/DOB/ID-document columns.';
COMMENT ON COLUMN users.email IS
    'Login identifier. Case-insensitive uniqueness enforced by ux_users_email_lower on lower(email).';
COMMENT ON COLUMN users.password_hash IS
    'Salted adaptive one-way hash (Req 2.4), never plaintext. Nullable until set (task 9.2).';
COMMENT ON COLUMN users.lockout_until IS
    'TIMESTAMPTZ until which login is locked (Req 2.8); NULL when not locked. Logic in task 10.2.';

-- Case-insensitive unique email: a single functional UNIQUE index on lower(email) gives the
-- "email UQ" of the design without requiring the citext extension. Two accounts differing only
-- by case collide here.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

-- Keep updated_at authoritative server-side on every UPDATE (shared V1 trigger function).
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();


-- =============================================================================
-- TABLE 2 — user_society_memberships (TENANT-OWNED)
-- =============================================================================
--
-- Tenant-owned:
--   A membership scopes a user's role within exactly ONE society (Req 3.6, A2), so it carries
--   `society_id UUID NOT NULL REFERENCES societies(id)` per the tenant-ownership convention
--   (design "Conventions", Req 1.1). It is therefore subject to the tenant-isolation layer:
--   the Hibernate societyFilter / base-repository predicate (task 7.3) and the defense-in-depth
--   `tenant_isolation` RLS policy (task 8.2) apply to this table.
--
--   NOTE (coordination with task 7.3): the shared TenantOwnedEntity base class / @Filter
--   convention is introduced concurrently in task 7.3. This migration models the society_id
--   column, its FK, and the tenant-first index/constraints now so the schema is complete; the
--   JPA entity maps society_id explicitly and carries a TODO to adopt the shared base class /
--   @Filter once 7.3 lands. The RLS policy itself is added table-by-table in task 8.2.
--
--   Platform_Admin has NO membership row: its scope is platform-wide and cross-tenant access
--   runs through a distinct audited path (design users/memberships note, Req 1.14, 3.5). The
--   PLATFORM_ADMIN role value still exists in the role set, but is not expressed as a
--   per-society membership.
--
-- Columns (design "user_society_memberships — id PK; user_id FK; society_id FK NOT NULL;
--   role (enum of the 11 roles, Req 3.1); scope (assigned-flat/building for scoped roles);
--   status. UQ (user_id, society_id, role). IX (society_id, role)"):
--   * id         — UUID PK, uuid_generate_v7() default.
--   * user_id    — FK -> users(id), NOT NULL. A membership always belongs to a user.
--   * society_id — FK -> societies(id), NOT NULL. The one society this membership scopes.
--   * role       — one of the 11 roles (Req 3.1), constrained by CHECK to the authoritative
--                  set from the Permission Matrix / design: PLATFORM_ADMIN, SOCIETY_ADMIN,
--                  FACILITY_MANAGER, ACCOUNTANT, SECURITY_GUARD, OWNER, TENANT_RESIDENT,
--                  FAMILY_MEMBER, MAINTENANCE_STAFF, VENDOR, AUDITOR.
--   * scope      — OPTIONAL scoping payload for scoped roles (e.g. assigned flat/building id,
--                  or a small JSON document). NULL for society-wide roles. Modeled as TEXT so a
--                  bare UUID or a compact JSON value both fit without committing the schema to
--                  one shape before the authorization model (task 12.x) finalizes it.
--   * status     — membership lifecycle, constrained to 'active'/'ended'. Default 'active'.
--                  Ending a membership is a status flip (history retained), not a delete.
--   * created_at/updated_at — TIMESTAMPTZ (UTC); updated_at kept current by set_updated_at().
--
-- Uniqueness & index:
--   * UQ (user_id, society_id, role) — a user holds a given role in a given society at most
--     once (design UQ; Req 3.6).
--   * IX (society_id, role) — tenant-first index supporting "all members with role R in society
--     S" (design IX), the common authorization lookup.
-- =============================================================================
CREATE TABLE user_society_memberships (
    id         UUID         NOT NULL DEFAULT uuid_generate_v7(),
    user_id    UUID         NOT NULL,
    society_id UUID         NOT NULL,
    role       TEXT         NOT NULL,
    scope      TEXT         NULL,
    status     TEXT         NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_society_memberships PRIMARY KEY (id),
    -- A membership always belongs to a real user and a real society.
    CONSTRAINT fk_memberships_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_memberships_society FOREIGN KEY (society_id) REFERENCES societies(id),
    -- Role is the closed set of 11 roles (Req 3.1); enforce at the DB so no path can store an
    -- unknown role. Values align with the design / Permission Matrix role names.
    CONSTRAINT ck_memberships_role CHECK (role IN (
        'PLATFORM_ADMIN',
        'SOCIETY_ADMIN',
        'FACILITY_MANAGER',
        'ACCOUNTANT',
        'SECURITY_GUARD',
        'OWNER',
        'TENANT_RESIDENT',
        'FAMILY_MEMBER',
        'MAINTENANCE_STAFF',
        'VENDOR',
        'AUDITOR'
    )),
    -- Membership lifecycle is a closed set; ending is a status flip, not a delete (history kept).
    CONSTRAINT ck_memberships_status CHECK (status IN ('active', 'ended')),
    -- A user holds a given role in a given society at most once (design UQ; Req 3.6).
    CONSTRAINT uq_memberships_user_society_role UNIQUE (user_id, society_id, role)
);

COMMENT ON TABLE user_society_memberships IS
    'Tenant-owned: a user''s role within exactly one society (Req 3.6, A2). Subject to the '
    'tenant-isolation layer (Hibernate societyFilter / base repo predicate, task 7.3) and the '
    'tenant_isolation RLS policy (task 8.2). Platform_Admin holds no membership row (Req 1.14, 3.5).';
COMMENT ON COLUMN user_society_memberships.role IS
    'One of the 11 roles (Req 3.1), constrained by ck_memberships_role; names match the Permission Matrix.';
COMMENT ON COLUMN user_society_memberships.scope IS
    'Optional scoping payload for scoped roles (e.g. assigned flat/building id or compact JSON); '
    'NULL for society-wide roles.';
COMMENT ON COLUMN user_society_memberships.status IS
    'Membership lifecycle: active | ended. Ending is a status flip; history is retained.';

-- Tenant-first index: members with a given role in a given society (authorization lookup).
CREATE INDEX ix_memberships_society_role
    ON user_society_memberships (society_id, role);

-- Supports looking up all of a single user's memberships (e.g. at login, to build their roles).
CREATE INDEX ix_memberships_user
    ON user_society_memberships (user_id);

-- Keep updated_at authoritative server-side on every UPDATE (shared V1 trigger function).
CREATE TRIGGER trg_memberships_set_updated_at
    BEFORE UPDATE ON user_society_memberships
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
