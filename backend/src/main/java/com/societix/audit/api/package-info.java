/**
 * Public API of the <strong>audit</strong> module.
 *
 * <p>Responsibility: append-only audit records written transactionally with the originating
 * state change.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: {@code tenancy} (foundation) for server-side tenant scoping of audit
 * queries, plus {@code common}. No domain-module deps. Other modules must not reach into
 * {@code com.societix.audit.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.audit.api;
