/**
 * Public API of the <strong>visitor</strong> module.
 *
 * <p>Responsibility: visitor entries/exits and approval coordination.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: societyproperty, resident, notification, audit. Other modules must
 * not reach into {@code com.societix.visitor.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.visitor.api;
