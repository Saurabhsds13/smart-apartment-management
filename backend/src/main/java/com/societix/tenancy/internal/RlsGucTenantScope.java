package com.societix.tenancy.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantScope;

/**
 * Default {@link TenantScope}: binds the request's server-derived owning Society to a JDBC
 * connection as the PostgreSQL GUC {@code app.current_society}, the variable the Row-Level Security
 * policies (task 8.2) compare {@code society_id} against. This is the database-enforced
 * defense-in-depth layer complementing the application-layer {@code societyFilter} (ADR-2;
 * Req 1.5, 1.10).
 *
 * <h2>Why {@code SET LOCAL} and a {@code UUID}, not string concatenation of untrusted input</h2>
 * The society is read only from {@link TenantContext#currentSocietyId()} (server-derived; task 7.2)
 * and is a {@link UUID}, never a client string. {@code SET} / {@code SET LOCAL} in PostgreSQL does
 * not accept a bound {@code ?} parameter for its value through the JDBC protocol, so the value must
 * be inlined into the statement text. To keep that safe, the value is a {@link UUID}: rendering it
 * via {@link UUID#toString()} yields a fixed 36-character canonical form drawn solely from
 * {@code [0-9a-f-]}, which cannot contain a quote, semicolon, or any other SQL metacharacter. There
 * is therefore no injection surface — the composed statement is structurally
 * {@code SET LOCAL app.current_society = '<canonical-uuid>'}.
 *
 * <h2>Transaction scoping</h2>
 * {@code SET LOCAL} scopes the GUC to the surrounding transaction, so PostgreSQL clears it
 * automatically on commit or rollback. The {@link #clearOnReturn(Connection)} {@code RESET} is a
 * redundant belt-and-braces reset performed by the pool on connection return (Req 1.10).
 */
@Component
public class RlsGucTenantScope implements TenantScope {

    /** The PostgreSQL session variable (GUC) the RLS policies read via {@code current_setting}. */
    static final String GUC_NAME = "app.current_society";

    private final TenantContext tenantContext;

    /**
     * @param tenantContext the request-scoped tenant context (injected as a scoped proxy so the
     *     per-request society is observed on each call)
     */
    public RlsGucTenantScope(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    /**
     * Composes the {@code SET LOCAL app.current_society = '<uuid>'} statement for the given society.
     * Package-visible so the statement composition can be unit-tested without a live database.
     *
     * <p>The {@code societyId} is a {@link UUID}, so the interpolated literal is always a canonical
     * UUID string and cannot carry SQL metacharacters — see the class Javadoc.
     *
     * @param societyId the server-derived owning Society (must not be {@code null})
     * @return the exact SQL text to execute
     */
    static String setLocalStatement(UUID societyId) {
        // UUID.toString() is a fixed canonical [0-9a-f-] form; no escaping/injection surface.
        return "SET LOCAL " + GUC_NAME + " = '" + societyId + "'";
    }

    /**
     * Composes the {@code RESET app.current_society} statement. Package-visible for unit testing.
     *
     * @return the exact SQL text to execute
     */
    static String resetStatement() {
        return "RESET " + GUC_NAME;
    }

    @Override
    public void applyToSession(Connection connection) throws SQLException {
        // Resolve the server-derived society FIRST. If unresolved/platform-admin this throws before
        // any statement runs, so the operation aborts rather than binding a wrong or empty GUC
        // (mirrors the application-layer filter's resolve-first ordering; Req 1.6).
        UUID societyId = tenantContext.currentSocietyId();
        try (Statement statement = connection.createStatement()) {
            statement.execute(setLocalStatement(societyId));
        }
    }

    @Override
    public void clearOnReturn(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(resetStatement());
        }
    }
}
