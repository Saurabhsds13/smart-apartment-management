/**
 * Public, cross-cutting building blocks of the platform (the <strong>common</strong> module).
 *
 * <p>Responsibility: shared contracts usable by every module — the RFC 7807 error envelope,
 * base DTOs, pagination types, and shared configuration. Because these are intended to be
 * shared, any module may depend on {@code com.societix.common.api}; common itself depends on
 * no other module.
 */
package com.societix.common.api;
