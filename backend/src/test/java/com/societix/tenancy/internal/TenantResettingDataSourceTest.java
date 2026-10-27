package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.tenancy.api.TenantScope;

/**
 * Unit tests for {@link TenantResettingDataSource} — the connection-pool on-return hook that scrubs
 * the tenant GUC before a connection is returned to the pool (ADR-2; Req 1.10). The real Hikari pool
 * is not needed: a mocked target {@link DataSource} and {@link Connection} let us assert that the
 * wrapper issues the RESET on {@code close()} and forwards everything else unchanged.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantResettingDataSource")
class TenantResettingDataSourceTest {

    @Mock private DataSource target;
    @Mock private Connection pooled;
    @Mock private TenantScope tenantScope;

    @Test
    @DisplayName("closing a borrowed connection RESETs the GUC before returning it to the pool (Req 1.10)")
    void resetsGucBeforeReturningToPool() throws SQLException {
        when(target.getConnection()).thenReturn(pooled);
        when(pooled.isClosed()).thenReturn(false);

        TenantResettingDataSource ds = new TenantResettingDataSource(target, tenantScope);
        Connection borrowed = ds.getConnection();
        borrowed.close();

        // RESET must happen first, THEN the real close returns the physical connection to Hikari.
        InOrder order = Mockito.inOrder(tenantScope, pooled);
        order.verify(tenantScope).clearOnReturn(pooled);
        order.verify(pooled).close();
    }

    @Test
    @DisplayName("ordinary calls pass straight through to the pooled connection")
    void passesThroughNonCloseCalls() throws SQLException {
        when(target.getConnection()).thenReturn(pooled);
        when(pooled.getAutoCommit()).thenReturn(true);

        TenantResettingDataSource ds = new TenantResettingDataSource(target, tenantScope);
        Connection borrowed = ds.getConnection();

        assertThat(borrowed.getAutoCommit()).isTrue();
        verify(pooled).getAutoCommit();
        verify(tenantScope, never()).clearOnReturn(Mockito.any());
    }

    @Test
    @DisplayName("a failed RESET is swallowed and the connection is still returned to the pool")
    void resetFailureStillReturnsConnection() throws SQLException {
        when(target.getConnection()).thenReturn(pooled);
        when(pooled.isClosed()).thenReturn(false);
        Mockito.doThrow(new SQLException("reset failed")).when(tenantScope).clearOnReturn(pooled);

        TenantResettingDataSource ds = new TenantResettingDataSource(target, tenantScope);
        Connection borrowed = ds.getConnection();
        borrowed.close();

        // Best-effort reset: a transient failure must never wedge the pool — close still happens.
        verify(pooled).close();
    }

    @Test
    @DisplayName("an already-closed connection is not reset (nothing to leak)")
    void doesNotResetAlreadyClosedConnection() throws SQLException {
        when(target.getConnection()).thenReturn(pooled);
        when(pooled.isClosed()).thenReturn(true);

        TenantResettingDataSource ds = new TenantResettingDataSource(target, tenantScope);
        Connection borrowed = ds.getConnection();
        borrowed.close();

        verify(tenantScope, never()).clearOnReturn(Mockito.any());
        verify(pooled).close();
    }
}
