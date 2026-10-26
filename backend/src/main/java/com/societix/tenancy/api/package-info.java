/**
 * Public API of the <strong>tenancy</strong> module.
 *
 * <p>Responsibility: Society entity, tenant provisioning/suspension, TenantContext holder,
 * and tenant-scope enforcement primitives.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: foundation (no domain deps). Other modules must not reach into
 * {@code com.societix.tenancy.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.tenancy.api;
