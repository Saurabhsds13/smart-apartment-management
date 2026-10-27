package com.societix.tenancy.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Enforcement primitive that binds the request's server-derived owning Society to a JDBC
 * {@link Connection} as a PostgreSQL session variable (GUC), so the <strong>database-enforced</strong>
 * Row-Level Security layer (ADR-2, defense-in-depth) sees the same society the application-layer
 * Hibernate filter uses (Req 1.5, 1.10).
 *
 * <h2>Two independent isolation layers</h2>
 * The primary isolation layer is the application-layer {@code societyFilter} enabled on every
 * Hibernate session (ADR-2, task 7.3). This {@code TenantScope} is the <em>second</em>,
 * database-enforced layer: it sets the GUC {@code app.current_society} that the RLS policies added in
 * task 8.2 compare against via {@code current_setting('app.current_society', true)::uuid}. Both
 * layers must independently fail before any cross-tenant row could be read or written, which is the
 * whole point of defense-in-depth.
 *
 * <h2>Transaction-scoped set, pool-return reset</h2>
 * <ul>
 *   <li>{@link #applyToSession(Connection)} issues {@code SET LOCAL app.current_society = '<uuid>'}
 *       at transaction start, on the same connection that runs the transaction's queries. Because
 *       {@code SET LOCAL} is scoped to the surrounding transaction, PostgreSQL automatically clears
 *       it on commit or rollback — the GUC can never outlive the transaction that set it, so it
 *       cannot leak to the next borrower of a pooled connection (Req 1.10).</li>
 *   <li>{@link #clearOnReturn(Connection)} issues {@code RESET app.current_society} and is invoked
 *       by the connection pool's on-return hook. With {@code SET LOCAL} this reset is strictly
 *       redundant, but it is kept as a belt-and-braces guarantee: even a connection used
 *       <em>outside</em> a transaction, or a future change that switched to a session-level
 *       {@code SET}, could never carry one request's society into the next (Req 1.10).</li>
 * </ul>
 *
 * <h2>Server-derived society only (Req 1.5, BR1)</h2>
 * The society written to the GUC is read exclusively from {@link TenantContext} (populated solely
 * from the authenticated session, task 7.2). This interface exposes no method that accepts a
 * caller-supplied society identifier, so a client cannot influence the GUC through any header,
 * parameter, path, or body. The identifier is carried as a {@link java.util.UUID}, not a free-form
 * string, so the value interpolated into the {@code SET LOCAL} statement is structurally a canonical
 * UUID and cannot be an injection vector.
 */
public interface TenantScope {

    /**
     * Binds the current request's server-derived owning Society to the given connection for the
     * duration of the current transaction, by issuing {@code SET LOCAL app.current_society = '<uuid>'}.
     *
     * <p>Intended to be called at transaction start, from the same place the application-layer
     * Hibernate filter is enabled, on the connection backing the active transaction.
     *
     * @param connection the connection running the current transaction (must not be {@code null})
     * @throws SQLException if the {@code SET LOCAL} statement cannot be executed
     * @throws TenantContextUnresolvedException if no owning Society is resolved for the request; the
     *     caller must treat this as an abort (no tenant-owned access proceeds), consistent with the
     *     application-layer behaviour (Req 1.6)
     */
    void applyToSession(Connection connection) throws SQLException;

    /**
     * Clears the {@code app.current_society} GUC on the given connection by issuing
     * {@code RESET app.current_society}. Invoked by the connection pool when a connection is returned,
     * as a belt-and-braces guarantee that one request's society can never leak to the next borrower
     * of a reused connection (Req 1.10).
     *
     * @param connection the connection being returned to the pool (must not be {@code null})
     * @throws SQLException if the {@code RESET} statement cannot be executed
     */
    void clearOnReturn(Connection connection) throws SQLException;
}
