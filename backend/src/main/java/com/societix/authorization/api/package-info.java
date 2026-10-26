/**
 * Public API of the <strong>authorization</strong> module.
 *
 * <p>Responsibility: role/permission model, Permission Matrix enforcement, and method- and
 * object-level authorization checks.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: identity, tenancy. Other modules must not reach into
 * {@code com.societix.authorization.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.authorization.api;
