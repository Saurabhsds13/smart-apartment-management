package com.societix.identity.internal;

/**
 * Account lifecycle state of a {@link User} (design.md "Data Models" &gt; users; Req 2.1, 2.7, 2.8).
 *
 * <p>The set is closed and mirrored by the {@code ck_users_status} CHECK constraint in
 * {@code V4__users_memberships.sql}, so no path (app or raw SQL) can persist an out-of-range value.
 *
 * <p>Persisted by name via {@link jakarta.persistence.EnumType#STRING} so the stored values match
 * the lowercase tokens in the DB CHECK constraint ({@code 'active'} / {@code 'locked'} /
 * {@code 'disabled'}); the constants are declared in lowercase to line up with those tokens.
 */
public enum UserStatus {

    /** The account is usable. New accounts are created in this state (DB default). */
    active,

    /** The account is temporarily locked (e.g. after repeated failed logins, Req 2.7/2.8). */
    locked,

    /** The account is administratively disabled; it cannot sign in. */
    disabled
}
