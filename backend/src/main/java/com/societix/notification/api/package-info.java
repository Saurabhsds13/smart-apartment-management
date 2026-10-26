/**
 * Public API of the <strong>notification</strong> module.
 *
 * <p>Responsibility: in-app notifications and the external channel abstraction (email).
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: identity. Other modules must not reach into
 * {@code com.societix.notification.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.notification.api;
