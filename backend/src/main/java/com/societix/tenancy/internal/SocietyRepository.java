package com.societix.tenancy.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Society} (task 7.1).
 *
 * <p>Lives in {@code tenancy.internal} alongside its entity: it is part of the tenancy module's
 * internal persistence, not its public contract (ArchUnit enforces that only
 * {@code com.societix.tenancy.api} is visible across modules). Provisioning and suspension services
 * built on top of this repository arrive in tasks 32.1/32.2.
 *
 * <p>{@code societies} is the platform-level tenant root and is <strong>not</strong> tenant-owned,
 * so this repository is intentionally <em>not</em> routed through the central tenant-scoping base
 * repository introduced for tenant-owned entities in task 7.3. Standard CRUD from
 * {@link JpaRepository} is the full surface this task requires.
 */
public interface SocietyRepository extends JpaRepository<Society, UUID> {
}
