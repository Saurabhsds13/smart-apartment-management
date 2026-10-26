/**
 * Public API of the <strong>communication</strong> module.
 *
 * <p>Responsibility: announcements and targeting.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: resident, notification. Other modules must not reach into
 * {@code com.societix.communication.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.communication.api;
