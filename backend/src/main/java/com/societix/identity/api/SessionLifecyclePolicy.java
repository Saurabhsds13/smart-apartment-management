package com.societix.identity.api;

import java.time.Duration;
import java.util.Set;

/**
 * Pure, side-effect-free policy for server-side session lifetime (task 10.2; design.md ADR-3;
 * Req 2.10, 2.11, 2.12).
 *
 * <p>The servlet container enforces only a single global idle timeout, so the richer role-aware
 * rules are evaluated per request against this policy and the owning session is invalidated when a
 * limit is exceeded. Two independent limits apply:
 * <ul>
 *   <li><strong>Idle timeout</strong> ? time since the session was last accessed. Privileged roles
 *       (Platform_Admin, Society_Admin, Accountant, Facility_Manager) time out after
 *       {@value #PRIVILEGED_IDLE_MINUTES} minutes (Req 2.10); resident roles (Owner,
 *       Tenant_Resident, Family_Member) after {@value #RESIDENT_IDLE_MINUTES} minutes
 *       (Req 2.11). Any unlisted/mixed role uses the stricter privileged timeout.</li>
 *   <li><strong>Absolute lifetime</strong> ? time since the session was established; a hard cap of
 *       {@value #ABSOLUTE_LIFETIME_HOURS} hours regardless of activity (Req 2.12).</li>
 * </ul>
 *
 * <p>This class is intentionally free of Spring, servlet, and clock dependencies so it can be unit
 * tested exhaustively; the filter that owns the session supplies the elapsed durations and the
 * actor's roles.
 */
public final class SessionLifecyclePolicy {

    /** Idle timeout (minutes) for privileged roles ? admin/finance/facility (Req 2.10). */
    public static final int PRIVILEGED_IDLE_MINUTES = 30;

    /** Idle timeout (minutes) for resident roles ? owner/tenant/family (Req 2.11). */
    public static final int RESIDENT_IDLE_MINUTES = 60;

    /** Absolute maximum session lifetime (hours) from establishment, regardless of activity (Req 2.12). */
    public static final int ABSOLUTE_LIFETIME_HOURS = 12;

    /** Role names (see the identity membership roles) that get the longer resident idle window. */
    private static final Set<String> RESIDENT_ROLES =
            Set.of("OWNER", "TENANT_RESIDENT", "FAMILY_MEMBER");

    private SessionLifecyclePolicy() {
        // static policy holder
    }

    /**
     * Returns the idle timeout that applies to an actor holding the given roles. The longer resident
     * window applies only when <em>every</em> role the actor holds is a resident role; if the actor
     * holds any privileged (or otherwise unlisted) role, the stricter privileged timeout applies, so
     * a privileged session can never be granted the more lenient resident idle window (Req 2.10).
     *
     * @param roles the role names held by the actor (may be empty)
     * @return the idle timeout for the actor
     */
    public static Duration idleTimeoutFor(Set<String> roles) {
        boolean allResident = roles != null && !roles.isEmpty()
                && RESIDENT_ROLES.containsAll(roles);
        return Duration.ofMinutes(allResident ? RESIDENT_IDLE_MINUTES : PRIVILEGED_IDLE_MINUTES);
    }

    /** The absolute session-lifetime cap (Req 2.12). */
    public static Duration absoluteLifetime() {
        return Duration.ofHours(ABSOLUTE_LIFETIME_HOURS);
    }

    /**
     * Decides whether a session must be invalidated now, given how long it has been idle and how
     * long since it was established, for an actor holding the given roles.
     *
     * @param idle    time since the session was last accessed
     * @param age     time since the session was established
     * @param roles   the actor's role names
     * @return {@code true} if the session has exceeded its idle timeout or absolute lifetime
     */
    public static boolean isExpired(Duration idle, Duration age, Set<String> roles) {
        return idle.compareTo(idleTimeoutFor(roles)) >= 0
                || age.compareTo(absoluteLifetime()) >= 0;
    }
}
