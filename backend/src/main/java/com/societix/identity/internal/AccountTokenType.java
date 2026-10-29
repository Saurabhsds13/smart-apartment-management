package com.societix.identity.internal;

/**
 * Kind of single-use account credential (task 10.3; Req 2.5, 2.6, 2.14). Persisted by name as
 * lowercase text matching the {@code ck_account_tokens_type} CHECK constraint in
 * {@code V7__account_tokens.sql}.
 */
public enum AccountTokenType {
    /** A password-reset credential (Req 2.5, 2.6). */
    password_reset,
    /** An email-verification credential (Req 2.14). */
    email_verification
}
