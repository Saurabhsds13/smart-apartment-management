package com.societix.tenancy.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.societix.tenancy.api.TenantScope;

/**
 * {@link DataSource} decorator that implements the connection-pool <em>on-return</em> hook described
 * in ADR-2: when a borrowed connection is returned to the pool (its {@code close()} is called), this
 * wrapper first issues {@code RESET app.current_society} via {@link TenantScope#clearOnReturn}. This
 * is the belt-and-braces guarantee that one request's tenant GUC can never leak to the next borrower
 * of a reused pooled connection (Req 1.10).
 *
 * <h2>Why a wrapper rather than a native pool callback</h2>
 * HikariCP (the pool Spring Boot auto-configures) exposes no public per-return callback. The
 * portable, pool-agnostic way to run code at return time is to intercept the one signal that
 * <em>means</em> "return to pool": {@link Connection#close()}. Each connection handed out by
 * {@link #getConnection()} is wrapped in a lightweight {@link Proxy} whose {@code close()} performs
 * the {@code RESET} and then delegates to the real (pooled) {@code close()}, which hands the physical
 * connection back to Hikari. All other calls pass straight through.
 *
 * <h2>Relationship to {@code SET LOCAL}</h2>
 * Because {@link RlsGucTenantScope} uses {@code SET LOCAL}, the GUC is already cleared by PostgreSQL
 * at transaction end, so this reset is normally redundant. It is retained deliberately as a second
 * safety net: a connection used outside a transaction, a rolled-back-then-reused connection, or any
 * future switch to a session-level {@code SET} would still be scrubbed before the next borrower sees
 * it. The reset is best-effort — if it fails, the failure is logged and the connection is still
 * returned, so a transient reset error cannot wedge the pool.
 */
public class TenantResettingDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantResettingDataSource.class);

    private final TenantScope tenantScope;

    /**
     * @param targetDataSource the real (Hikari) pooled data source to delegate to
     * @param tenantScope the enforcement primitive whose {@code clearOnReturn} issues the RESET
     */
    public TenantResettingDataSource(DataSource targetDataSource, TenantScope tenantScope) {
        super(targetDataSource);
        this.tenantScope = tenantScope;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection pooled) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ResetOnCloseHandler(pooled, tenantScope));
    }

    /**
     * Intercepts {@link Connection#close()} to run the tenant-GUC reset before the physical
     * connection is returned to the pool. Every other method is forwarded unchanged to the pooled
     * connection.
     */
    private static final class ResetOnCloseHandler implements InvocationHandler {

        private final Connection delegate;
        private final TenantScope tenantScope;

        private ResetOnCloseHandler(Connection delegate, TenantScope tenantScope) {
            this.delegate = delegate;
            this.tenantScope = tenantScope;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("close".equals(name)) {
                resetQuietly();
                delegate.close();
                return null;
            }
            // Keep identity/equality sane for a proxy, and let everything else pass through.
            if ("unwrap".equals(name) && args != null && args.length == 1 && args[0] == Connection.class) {
                return delegate;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }

        private void resetQuietly() {
            if (delegate == null) {
                return;
            }
            try {
                // Only attempt the reset on a still-open connection; a closed one has nothing to leak.
                if (!delegate.isClosed()) {
                    tenantScope.clearOnReturn(delegate);
                }
            } catch (SQLException ex) {
                // Best-effort: never block returning the connection to the pool. SET LOCAL already
                // guarantees transaction-scoped reset, so this is defense-in-depth only (Req 1.10).
                log.warn("Failed to RESET app.current_society on connection return; "
                        + "SET LOCAL still bounds the GUC to its transaction", ex);
            }
        }
    }
}
