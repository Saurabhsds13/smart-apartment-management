package com.societix.identity.api;

import java.util.Set;

/**
 * Determines which roles MUST complete MFA to sign in (task 13.2; Req 24.1, 24.2, 24.3, BR13).
 *
 * <p>MFA is <em>enforced</em> for the platform super administrator and for privileged finance and
 * society-administration roles; it is <em>offered</em> (optional) to residents. A privileged
 * sign-in that has not completed an MFA challenge must be rejected (Req 24.9), so the sign-in flow
 * consults {@link #isMfaRequiredFor} to decide whether a second factor is mandatory for the actor's
 * roles.
 */
public final class MfaPolicy {

    /** Roles for which MFA is mandatory (Req 24.1, 24.2). */
    private static final Set<String> MFA_REQUIRED_ROLES = Set.of(
            "PLATFORM_ADMIN",   // platform super administrator (Req 24.1)
            "SOCIETY_ADMIN",    // privileged society administration (Req 24.2)
            "ACCOUNTANT",       // privileged finance (Req 24.2)
            "FACILITY_MANAGER");// privileged operations (Req 24.2, treated as privileged admin)

    private MfaPolicy() {
    }

    /**
     * Whether any role in the set makes MFA mandatory for the actor.
     *
     * @param roleNames the actor's role names (e.g. from memberships)
     * @return {@code true} if MFA must be completed before the actor is authenticated
     */
    public static boolean isMfaRequiredFor(Set<String> roleNames) {
        if (roleNames == null) {
            return false;
        }
        return roleNames.stream().anyMatch(MFA_REQUIRED_ROLES::contains);
    }

    /** Whether the given single role makes MFA mandatory. */
    public static boolean isMfaRequiredForRole(String roleName) {
        return MFA_REQUIRED_ROLES.contains(roleName);
    }
}
