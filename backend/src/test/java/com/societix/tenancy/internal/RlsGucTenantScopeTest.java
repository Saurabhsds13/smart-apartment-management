package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;

/**
 * Focused unit tests for {@link RlsGucTenantScope} — the database-layer defense-in-depth primitive
 * that binds the server-derived society to the {@code app.current_society} GUC used by PostgreSQL
 * RLS (ADR-2; Req 1.5, 1.10). These tests exercise the statement composition and the resolve-first
 * abort without a live database; the real RLS enforcement is proven by the Testcontainers test in
 * task 8.3.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RlsGucTenantScope")
class RlsGucTenantScopeTest {

    /** Canonical UUID text: 8-4-4-4-12 lowercase hex — the only shape UUID.toString() can produce. */
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Mock private TenantContext tenantContext;
    @Mock private Connection connection;
    @Mock private Statement statement;

    @Test
    @DisplayName("composes SET LOCAL app.current_society with the canonical UUID literal (Req 1.5)")
    void composesSetLocalStatement() {
        UUID societyId = UUID.fromString("3f1d9c0a-2b4e-4c6a-8d7f-0a1b2c3d4e5f");

        String sql = RlsGucTenantScope.setLocalStatement(societyId);

        assertThat(sql).isEqualTo("SET LOCAL app.current_society = '3f1d9c0a-2b4e-4c6a-8d7f-0a1b2c3d4e5f'");
    }

    @Test
    @DisplayName("composes RESET app.current_society")
    void composesResetStatement() {
        assertThat(RlsGucTenantScope.resetStatement()).isEqualTo("RESET app.current_society");
    }

    @Test
    @DisplayName("the GUC literal is always a canonical UUID, so there is no SQL-injection surface (BR1)")
    void gucLiteralIsAlwaysACanonicalUuid() {
        // The society is carried as a UUID, never a free-form string. Across many random UUIDs the
        // interpolated literal is always the fixed [0-9a-f-] canonical form — it can never contain a
        // quote, semicolon, whitespace, or any other SQL metacharacter.
        for (int i = 0; i < 1000; i++) {
            UUID id = UUID.randomUUID();
            String sql = RlsGucTenantScope.setLocalStatement(id);
            String literal = sql.substring(sql.indexOf('\'') + 1, sql.length() - 1);

            assertThat(literal).matches(CANONICAL_UUID);
            assertThat(literal).isEqualTo(id.toString());
            // Exactly one single-quoted literal; nothing could terminate it early.
            assertThat(sql.chars().filter(c -> c == '\'').count()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("applyToSession executes SET LOCAL with the server-derived society (Req 1.5)")
    void applyToSessionExecutesSetLocal() throws Exception {
        UUID societyId = UUID.randomUUID();
        when(tenantContext.currentSocietyId()).thenReturn(societyId);
        when(connection.createStatement()).thenReturn(statement);

        new RlsGucTenantScope(tenantContext).applyToSession(connection);

        verify(statement).execute(RlsGucTenantScope.setLocalStatement(societyId));
        verify(statement).close();
    }

    @Test
    @DisplayName("applyToSession aborts (no statement) when the context is unresolved (Req 1.6)")
    void applyToSessionAbortsWhenUnresolved() throws Exception {
        when(tenantContext.currentSocietyId()).thenThrow(new TenantContextUnresolvedException());

        RlsGucTenantScope scope = new RlsGucTenantScope(tenantContext);

        assertThatThrownBy(() -> scope.applyToSession(connection))
                .isInstanceOf(TenantContextUnresolvedException.class);
        // Resolve-first: no connection work happens on an unresolved request, so no empty/wrong GUC
        // is ever set.
        verify(connection, never()).createStatement();
    }

    @Test
    @DisplayName("clearOnReturn executes RESET app.current_society (Req 1.10)")
    void clearOnReturnExecutesReset() throws Exception {
        when(connection.createStatement()).thenReturn(statement);

        new RlsGucTenantScope(tenantContext).clearOnReturn(connection);

        verify(statement).execute(RlsGucTenantScope.resetStatement());
        verify(statement).close();
    }
}
