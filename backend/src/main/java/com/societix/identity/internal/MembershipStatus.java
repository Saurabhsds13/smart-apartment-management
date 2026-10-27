package com.societix.identity.internal;

/**
 * Lifecycle state of a {@link UserSocietyMembership} (design.md "Data Models" &gt;
 * user_society_memberships; Req 3.6).
 *
 * <p>The set is closed and mirrored by the {@code ck_memberships_status} CHECK constraint in
 * {@code V4__users_memberships.sql}. Ending a membership is a status flip to {@link #ended}
 * (history retained), never a row delete.
 *
 * <p>Persisted by name via {@link jakarta.persistence.EnumType#STRING} so the stored values match
 * the lowercase tokens in the DB CHECK constraint ({@code 'active'} / {@code 'ended'}); the
 * constants are declared in lowercase to line up with those tokens.
 */
public enum MembershipStatus {

    /** The membership is in effect. New memberships are created in this state (DB default). */
    active,

    /** The membership has been ended; retained for history rather than deleted. */
    ended
}
