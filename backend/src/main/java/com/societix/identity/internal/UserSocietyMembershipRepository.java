package com.societix.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link UserSocietyMembership} (task 9.1).
 *
 * <p>Lives in {@code identity.internal}: internal persistence, not the module's public contract
 * (only {@code com.societix.identity.api} crosses the module boundary; ArchUnit enforces this).
 *
 * <p><strong>Tenant scoping (task 7.3 coordination).</strong> {@code user_society_memberships} is
 * tenant-owned. Once the shared {@code societyFilter} / base-repository predicate from task 7.3 is
 * in place, every query here is implicitly constrained to the resolved tenant. Until then, callers
 * that read across the membership table must pass the {@code society_id} explicitly; the finder
 * methods below take a {@code societyId} argument so the tenant predicate is always present.
 */
public interface UserSocietyMembershipRepository
        extends JpaRepository<UserSocietyMembership, UUID> {

    /**
     * Returns all memberships for a single user across the societies they belong to. Used at login
     * to assemble the user's roles. (A user is global; this spans societies by design.)
     *
     * @param userId the user's id
     * @return the user's memberships (possibly empty)
     */
    List<UserSocietyMembership> findByUserId(UUID userId);

    /**
     * Returns all memberships within one society that carry the given role — the tenant-first
     * authorization lookup backed by {@code ix_memberships_society_role}.
     *
     * @param societyId the society (tenant) id
     * @param role      the role to match
     * @return matching memberships (possibly empty)
     */
    List<UserSocietyMembership> findBySocietyIdAndRole(UUID societyId, MembershipRole role);

    /**
     * Resolves the single membership for a (user, society, role) triple, matching the
     * {@code uq_memberships_user_society_role} uniqueness rule.
     *
     * @param userId    the user's id
     * @param societyId the society (tenant) id
     * @param role      the role
     * @return the matching membership, if any
     */
    Optional<UserSocietyMembership> findByUserIdAndSocietyIdAndRole(
            UUID userId, UUID societyId, MembershipRole role);
}
