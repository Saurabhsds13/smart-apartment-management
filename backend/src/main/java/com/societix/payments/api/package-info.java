/**
 * Public API of the <strong>payments</strong> module.
 *
 * <p>Responsibility: receipts, credits, allocations, refunds, and the ledger.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: billing, audit. Other modules must not reach into
 * {@code com.societix.payments.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.payments.api;
