/**
 * Public API of the <strong>reporting</strong> module.
 *
 * <p>Responsibility: financial/operational reports (tenant-scoped, read-only).
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: billing, payments, tenancy. Other modules must not reach into
 * {@code com.societix.reporting.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.reporting.api;
