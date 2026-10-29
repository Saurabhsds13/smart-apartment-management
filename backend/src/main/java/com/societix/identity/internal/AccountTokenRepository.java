package com.societix.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AccountToken} (task 10.3). Platform-level (not tenant-owned),
 * so it is a plain {@link JpaRepository}, not routed through the tenant-scoped base repository.
 */
public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /**
     * Resolves a presented token by the one-way hash of its raw value and its kind. The hash is
     * UNIQUE, so this returns at most one row.
     *
     * @param tokenHash the one-way hash of the presented raw token
     * @param type      the expected token kind
     * @return the matching token, if any
     */
    Optional<AccountToken> findByTokenHashAndType(String tokenHash, AccountTokenType type);
}
