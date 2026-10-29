-- =============================================================================
-- V7__account_tokens.sql - single-use account credentials (reset + verification)
-- =============================================================================
--
-- Purpose (task 10.3; design.md "Identity & Auth"; Req 2.5, 2.6, 2.14):
--   Backs password-reset and email-verification flows with single-use, time-limited
--   credentials. Only a HASH of each token is stored (never the raw token), so a
--   database read cannot recover a usable credential; the raw token travels only in the
--   email to the user.
--
--   account_tokens is platform-level (keyed to a platform-level users row), so it carries
--   no society_id and is exempt from the tenant_isolation RLS policy, consistent with
--   users/societies.
--
-- Columns:
--   * id         - UUID PK, uuid_generate_v7() default.
--   * user_id    - FK -> users(id); the account the token acts on.
--   * type       - 'password_reset' | 'email_verification' (closed set, CHECK-constrained).
--   * token_hash - one-way hash (e.g. SHA-256 hex) of the raw single-use token. UNIQUE so a
--                  presented token resolves to at most one row; the raw token is never stored.
--   * expires_at - TIMESTAMPTZ; the token is invalid once now() >= expires_at (Req 2.5: reset
--                  expires 60 min after issuance).
--   * consumed_at- TIMESTAMPTZ, NULL until first successful use; set to enforce single-use so a
--                  replay is rejected (Req 2.6).
--   * created_at - issuance instant (UTC).
-- =============================================================================

CREATE TABLE account_tokens (
    id         UUID         NOT NULL DEFAULT uuid_generate_v7(),
    user_id    UUID         NOT NULL,
    type       TEXT         NOT NULL,
    token_hash TEXT         NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_account_tokens PRIMARY KEY (id),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_account_tokens_type CHECK (type IN ('password_reset', 'email_verification')),
    -- A presented token hash resolves to at most one row.
    CONSTRAINT uq_account_tokens_token_hash UNIQUE (token_hash)
);

COMMENT ON TABLE account_tokens IS
    'Single-use, time-limited account credentials for password reset (Req 2.5/2.6) and email '
    'verification (Req 2.14). Platform-level (keyed to users); no society_id, no RLS. Stores only '
    'a one-way hash of each token, never the raw value; consumed_at enforces single-use.';

-- Look up a presented token by its hash (the hot path on use).
CREATE INDEX ix_account_tokens_user_type ON account_tokens (user_id, type);
