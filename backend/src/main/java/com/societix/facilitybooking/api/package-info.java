/**
 * Public API of the <strong>facilitybooking</strong> module.
 *
 * <p>Responsibility: facilities, bookings, approval, and conflict prevention.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: societyproperty, resident, audit. Other modules must not reach into
 * {@code com.societix.facilitybooking.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.facilitybooking.api;
