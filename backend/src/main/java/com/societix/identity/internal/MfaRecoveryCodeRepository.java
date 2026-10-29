package com.societix.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link MfaRecoveryCode} (task 13.1). */
public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    /** Resolves a recovery code by its one-way hash (UNIQUE), for single-use consumption. */
    Optional<MfaRecoveryCode> findByCodeHash(String codeHash);
}
