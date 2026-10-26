/**
 * Public API of the <strong>filestorage</strong> module.
 *
 * <p>Responsibility: file metadata, brokered time-limited URLs, and upload validation.
 *
 * <p>Holds the in-process service interfaces and DTOs other modules may depend on.
 * Allowed dependencies: tenancy, authorization. Other modules must not reach into
 * {@code com.societix.filestorage.internal}; ArchUnit tests enforce this boundary.
 */
package com.societix.filestorage.api;
