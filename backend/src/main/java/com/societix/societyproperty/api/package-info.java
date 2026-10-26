/**
 * Public API of the <strong>societyproperty</strong> module.
 *
 * <p>Responsibility: societies' buildings, wings, and flats.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: tenancy, authorization, audit. Other modules must not reach into
 * {@code com.societix.societyproperty.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.societyproperty.api;
