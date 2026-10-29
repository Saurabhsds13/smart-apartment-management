-- =============================================================================
-- V8__mfa_credentials.sql - multi-factor authentication credentials (TOTP)
-- =============================================================================
--
-- Purpose (task 13.1; design.md "MFA"; Req 24.4, 24.5, 24.6):
--   Stores each user's TOTP second-factor enrollment. The shared secret is stored
--   ENCRYPTED at rest (never in plaintext), and the one-time recovery codes are stored
--   only as HASHES (single-use), so a database read cannot recover a usable second factor
--   or a usable recovery code (Req 24.6).
--
--   mfa_credentials is platform-level (keyed to a platform-level users row): no society_id,
--   exempt from tenant_isolation RLS, consistent with users/societies.
--
-- Columns:
--   * id               - UUID PK, uuid_generate_v7() default.
--   * user_id          - FK -> users(id); UNIQUE so each user has at most one TOTP enrollment.
--   * type             - factor type; 'totp' for now (CHECK-constrained, extensible later).
--   * secret_encrypted - the TOTP shared secret, encrypted at rest (never plaintext, Req 24.6).
--   * enabled          - false until the user confirms a first valid code (enrollment complete).
--   * created_at/updated_at - TIMESTAMPTZ (UTC); updated_at kept current by set_updated_at().
--
-- recovery codes live in a child table so each is individually single-use (consumed_at).
-- =============================================================================

CREATE TABLE mfa_credentials (
    id               UUID         NOT NULL DEFAULT uuid_generate_v7(),
    user_id          UUID         NOT NULL,
    type             TEXT         NOT NULL DEFAULT 'totp',
    secret_encrypted TEXT         NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_mfa_credentials PRIMARY KEY (id),
    CONSTRAINT fk_mfa_credentials_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_mfa_credentials_user UNIQUE (user_id),
    CONSTRAINT ck_mfa_credentials_type CHECK (type IN ('totp'))
);

COMMENT ON TABLE mfa_credentials IS
    'Per-user MFA enrollment (TOTP). Secret stored encrypted at rest; recovery codes hashed and '
    'single-use (Req 24.5/24.6). Platform-level (keyed to users); no society_id, no RLS.';
COMMENT ON COLUMN mfa_credentials.secret_encrypted IS
    'TOTP shared secret, ENCRYPTED at rest (Req 24.6). Never stored or logged in plaintext.';

CREATE TRIGGER trg_mfa_credentials_set_updated_at
    BEFORE UPDATE ON mfa_credentials
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- mfa_recovery_codes - single-use backup codes (hash only)
-- -----------------------------------------------------------------------------
CREATE TABLE mfa_recovery_codes (
    id              UUID         NOT NULL DEFAULT uuid_generate_v7(),
    credential_id   UUID         NOT NULL,
    code_hash       TEXT         NOT NULL,
    consumed_at     TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_mfa_recovery_codes PRIMARY KEY (id),
    CONSTRAINT fk_mfa_recovery_codes_credential
        FOREIGN KEY (credential_id) REFERENCES mfa_credentials(id) ON DELETE CASCADE,
    CONSTRAINT uq_mfa_recovery_code_hash UNIQUE (code_hash)
);

COMMENT ON TABLE mfa_recovery_codes IS
    'Single-use MFA recovery codes, stored as one-way hashes only (Req 24.5/24.6). consumed_at '
    'enforces single use.';

CREATE INDEX ix_mfa_recovery_codes_credential ON mfa_recovery_codes (credential_id);
