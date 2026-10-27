package com.societix.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link User} (task 9.1).
 *
 * <p>Lives in {@code identity.internal}: it is the identity module's internal persistence, not its
 * public contract (only {@code com.societix.identity.api} crosses the module boundary; ArchUnit
 * enforces this).
 *
 * <p>{@code users} is a platform-level table (not tenant-owned), so this repository is intentionally
 * <em>not</em> routed through the central tenant-scoping base repository. A single account is global
 * to the platform; tenant scoping applies to {@link UserSocietyMembership}, not to the account.
 *
 * <p>Email lookup is <strong>case-insensitive</strong> to match the {@code ux_users_email_lower}
 * functional unique index: callers may pass any casing and {@link #findByEmailIgnoreCase} normalizes
 * via {@code lower(...)} on both sides.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email, case-insensitively, consistent with the {@code lower(email)} uniqueness
     * rule. Used by later authentication tasks (10.x) to resolve the account for a login.
     *
     * @param email the email to look up (any casing)
     * @return the matching user, if any
     */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * Reports whether an account with the given email already exists, case-insensitively. Used to
     * give a clear, pre-insert duplicate check in addition to the DB unique index.
     *
     * @param email the email to check (any casing)
     * @return {@code true} if an account with that email (ignoring case) exists
     */
    @Query("select (count(u) > 0) from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
