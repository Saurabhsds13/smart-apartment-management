/**
 * Public API of the <strong>billing</strong> module.
 *
 * <p>Responsibility: versioned charge rules and invoice generation.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: societyproperty, audit. Other modules must not reach into
 * {@code com.societix.billing.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.billing.api;
