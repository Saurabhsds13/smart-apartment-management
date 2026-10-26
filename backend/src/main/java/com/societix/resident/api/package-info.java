/**
 * Public API of the <strong>resident</strong> module.
 *
 * <p>Responsibility: owner/tenant/family associations with history.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: societyproperty, audit. Other modules must not reach into
 * {@code com.societix.resident.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.resident.api;
