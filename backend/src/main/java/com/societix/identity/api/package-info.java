/**
 * Public API of the <strong>identity</strong> module.
 *
 * <p>Responsibility: user accounts, credentials, password hashing, sessions, MFA credentials,
 * and email verification.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: audit. Other modules must not reach into
 * {@code com.societix.identity.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.identity.api;
