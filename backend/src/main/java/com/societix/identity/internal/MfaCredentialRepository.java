package com.societix.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link MfaCredential} (task 13.1). Platform-level, not tenant-scoped. */
public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    /** Resolves a user's single TOTP enrollment, if any. */
    Optional<MfaCredential> findByUserId(UUID userId);
}
