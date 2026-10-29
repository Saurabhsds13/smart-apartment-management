-- =============================================================================
-- V6__user_lockout_tracking.sql — login throttling / lockout counters
-- =============================================================================
--
-- Purpose (task 10.2; design.md ADR-3; Req 2.7, 2.8):
--   Adds the two columns the account-lockout policy needs on top of the existing
--   users.failed_attempts / users.lockout_until (V4):
--     * failure_window_started_at — when the current rolling failed-attempt window began,
--       so failures are counted over a 15-minute window (Req 2.7) rather than forever.
--     * lockout_count — how many lockout CYCLES the account has incurred, driving the
--       progressively-longer (but bounded, never permanent) backoff (Req 2.8).
--
--   users is platform-level (not tenant-owned); these columns carry no society_id and the
--   table remains exempt from the tenant_isolation RLS policy, consistent with V4.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN failure_window_started_at TIMESTAMPTZ NULL,
    ADD COLUMN lockout_count             INTEGER     NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_lockout_count_non_negative CHECK (lockout_count >= 0);

COMMENT ON COLUMN users.failure_window_started_at IS
    'Start of the current rolling failed-attempt window (Req 2.7); NULL when no failures are pending.';
COMMENT ON COLUMN users.lockout_count IS
    'Number of lockout cycles incurred, driving progressive backoff (Req 2.8). Never yields a permanent lock.';
