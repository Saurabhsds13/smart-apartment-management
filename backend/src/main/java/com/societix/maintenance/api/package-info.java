/**
 * Public API of the <strong>maintenance</strong> module.
 *
 * <p>Responsibility: maintenance request lifecycle and assignment.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: resident, notification, audit. Other modules must not reach into
 * {@code com.societix.maintenance.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.maintenance.api;
