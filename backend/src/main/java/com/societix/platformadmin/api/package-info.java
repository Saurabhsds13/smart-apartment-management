/**
 * Public API of the <strong>platformadmin</strong> module.
 *
 * <p>Responsibility: society onboarding/suspension, audited drill-down, and platform metrics.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: tenancy, audit. Other modules must not reach into
 * {@code com.societix.platformadmin.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.platformadmin.api;
