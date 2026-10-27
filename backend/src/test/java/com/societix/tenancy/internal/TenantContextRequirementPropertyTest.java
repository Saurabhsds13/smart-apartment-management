package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;

import jakarta.persistence.EntityManager;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

/**
 * Property-based test for the <strong>tenant-context requirement for tenant-owned operations</strong>
 * (task 7.4; design.md Correctness Property 2; Req 1.3, 1.4, 1.6).
 *
 * <p><strong>Feature: societix-platform, Property 2: Tenant-context requirement for tenant-owned
 * operations</strong> — a tenant-owned operation may proceed only with a resolved
 * {@link TenantContext}, and when it proceeds it is always constrained to exactly that context's
 * server-derived society. Concretely, for all randomized sequences of per-request context states:
 * <ul>
 *   <li><strong>resolved to a society S</strong> — both isolation layers are armed with exactly S:
 *       {@link SocietyFilterActivator#activate(Session)} enables the
 *       {@value TenantOwnedEntity#FILTER_NAME} filter with {@code societyId = S}, and
 *       {@link RlsGucTenantScope#applyToSession(Connection)} issues
 *       {@code SET LOCAL app.current_society = 'S'}. The only value ever bound comes from the
 *       server-derived context, never a client-supplied identifier (Req 1.4, 1.3);</li>
 *   <li><strong>unresolved (anonymous)</strong> or <strong>platform-admin</strong> — both layers
 *       <em>fail closed</em>: each throws {@link TenantContextUnresolvedException} and neither
 *       enables the Hibernate filter nor sets the GUC, so a tenant-owned operation aborts and yields
 *       zero rows rather than running unscoped (Req 1.6).</li>
 * </ul>
 *
 * <h2>Why this is a faithful, Docker-free property</h2>
 * The invariant proven here is at the <em>mechanism</em> level: the two places that arm tenant
 * scoping ({@link SocietyFilterActivator} for the application-layer Hibernate filter and
 * {@link RlsGucTenantScope} for the database-layer RLS GUC) both resolve the society first and
 * refuse to proceed unless a single owning society is resolved, and when they do proceed they bind
 * exactly the server-derived society. That logic is pure and needs no database, so this test drives
 * the production classes against mocked {@link Session}/{@link Connection}/{@link TenantContext} and
 * runs fast and locally. The activator half uses the <em>real</em> {@link RlsGucTenantScope} so the
 * two layers are armed together exactly as in production. The complementary DB-backed proof that an
 * unresolved/cross-tenant request actually returns <em>zero rows</em> against a live PostgreSQL with
 * RLS enabled is covered by the Testcontainers tests in tasks 7.5 and 8.3; keeping this property
 * local avoids duplicating that and keeps it in the fast test tier.
 */
@org.junit.jupiter.api.Tag("property")
@net.jqwik.api.Tag("property")
class TenantContextRequirementPropertyTest {

    /**
     * The three mutually exclusive resolution states a request can be in for a tenant-owned
     * operation. Only {@link #RESOLVED} may proceed; the other two must fail closed.
     */
    private enum ContextState {
        /** An authenticated member of exactly one society — the one state that may proceed. */
        RESOLVED,
        /** An anonymous / pre-authentication request with no derivable society (Req 1.6). */
        UNRESOLVED,
        /** A Platform_Admin at platform scope with no single owning society (Req 1.6, 1.14). */
        PLATFORM_ADMIN
    }

    /** A generated per-request step: the context state and (when resolved) its server-derived society. */
    private record ContextCase(ContextState state, UUID societyId) {

        boolean isResolvedToSociety() {
            return state == ContextState.RESOLVED;
        }
    }

    /**
     * For all randomized sequences of per-request contexts, the application-layer
     * {@link SocietyFilterActivator} and the database-layer {@link RlsGucTenantScope} obey the
     * tenant-context requirement: proceed with exactly the server-derived society when resolved, and
     * fail closed (throw, arm nothing) when unresolved or platform-admin.
     *
     * <p><strong>Validates: Requirements 1.3, 1.4, 1.6</strong>
     */
    @Property(tries = 200)
    void tenantOwnedOperationRequiresResolvedContextAndIsConstrainedToIt(
            @ForAll("contextSequences") @Size(min = 1, max = 20) java.util.List<ContextCase> sequence) {
        for (ContextCase step : sequence) {
            assertFilterActivatorHonoursContract(step);
            assertRlsGucScopeHonoursContract(step);
        }
    }

    /**
     * Drives {@link SocietyFilterActivator#activate(Session)} — wired with the <em>real</em>
     * {@link RlsGucTenantScope} so both isolation layers are armed from the one resolved context,
     * exactly as in production — and asserts the application-layer half of Property 2.
     */
    private void assertFilterActivatorHonoursContract(ContextCase step) {
        TenantContext tenantContext = tenantContextFor(step);
        EntityManager entityManager = mock(EntityManager.class);
        RlsGucTenantScope tenantScope = new RlsGucTenantScope(tenantContext);
        Session session = mock(Session.class);
        Filter filter = mock(Filter.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        // Record the SQL the real RlsGucTenantScope executes when the activator arms the DB layer.
        java.util.List<String> executedSql = new java.util.ArrayList<>();
        captureExecutedSql(statement, executedSql);

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);

        if (step.isResolvedToSociety()) {
            when(session.enableFilter(TenantOwnedEntity.FILTER_NAME)).thenReturn(filter);
            withConnectionStatement(connection, statement);
            answerDoWorkWith(session, connection);

            activator.activate(session);

            // Proceeds, and is constrained to EXACTLY the server-derived society (Req 1.4) — never a
            // client-supplied value, because the only source of the bound id is TenantContext (Req 1.3).
            verify(session).enableFilter(TenantOwnedEntity.FILTER_NAME);
            verify(filter).setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, step.societyId());
            // The DB-layer GUC was armed, from the same resolved context, with the same society
            // (the real RlsGucTenantScope ran on the transaction connection via doWork).
            assertThat(executedSql)
                    .as("GUC armed with exactly the server-derived society")
                    .containsExactly(RlsGucTenantScope.setLocalStatement(step.societyId()));
        } else {
            // Fail closed: unresolved OR platform-admin aborts with the unresolved exception (Req 1.6).
            assertThatThrownBy(() -> activator.activate(session))
                    .isInstanceOf(TenantContextUnresolvedException.class);
            // The filter is NEVER enabled and the GUC is NEVER set, so no unscoped query can run.
            verify(session, never()).enableFilter(anyString());
            verify(session, never()).doWork(any());
            assertThat(executedSql)
                    .as("DB-layer GUC must not be armed on an unresolved/platform-admin request")
                    .isEmpty();
        }
    }

    /**
     * Drives {@link RlsGucTenantScope#applyToSession(Connection)} directly for one context case and
     * asserts the database-layer half of Property 2.
     */
    private void assertRlsGucScopeHonoursContract(ContextCase step) {
        TenantContext tenantContext = tenantContextFor(step);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);

        RlsGucTenantScope scope = new RlsGucTenantScope(tenantContext);

        if (step.isResolvedToSociety()) {
            try {
                when(connection.createStatement()).thenReturn(statement);
                scope.applyToSession(connection);

                // Sets the GUC to EXACTLY the server-derived society (Req 1.4) via the canonical-UUID
                // literal; no client-supplied value can reach it (Req 1.3).
                verify(statement).execute(RlsGucTenantScope.setLocalStatement(step.societyId()));
                verify(statement).close();
            } catch (SQLException impossible) {
                throw new AssertionError(impossible);
            }
        } else {
            // Fail closed: resolve-first means no statement is ever created on an unresolved/
            // platform-admin request, so no empty or wrong GUC is bound (Req 1.6).
            assertThatThrownBy(() -> scope.applyToSession(connection))
                    .isInstanceOf(TenantContextUnresolvedException.class);
            try {
                verify(connection, never()).createStatement();
            } catch (SQLException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    /**
     * Builds a mock {@link TenantContext} matching the generated state. Both the unresolved and the
     * platform-admin states make {@link TenantContext#currentSocietyId()} throw
     * {@link TenantContextUnresolvedException}, mirroring production: neither has a single owning
     * society, so a tenant-owned operation must fail closed for both (Req 1.6).
     */
    private static TenantContext tenantContextFor(ContextCase step) {
        TenantContext context = mock(TenantContext.class);
        switch (step.state()) {
            case RESOLVED -> when(context.currentSocietyId()).thenReturn(step.societyId());
            case UNRESOLVED, PLATFORM_ADMIN ->
                    when(context.currentSocietyId()).thenThrow(new TenantContextUnresolvedException());
        }
        return context;
    }

    private static void withConnectionStatement(Connection connection, Statement statement) {
        try {
            when(connection.createStatement()).thenReturn(statement);
        } catch (SQLException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Makes a mocked session run the {@link Work} it is given against the supplied connection. */
    private static void answerDoWorkWith(Session session, Connection connection) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Work work = invocation.getArgument(0);
            work.execute(connection);
            return null;
        }).when(session).doWork(any());
    }

    /**
     * Records every SQL string passed to {@link Statement#execute(String)} on the mocked statement,
     * so the property can read back the exact {@code SET LOCAL} text the real
     * {@link RlsGucTenantScope} composed when the activator armed the DB layer.
     */
    private static void captureExecutedSql(Statement statement, java.util.List<String> sink) {
        try {
            when(statement.execute(anyString())).thenAnswer(invocation -> {
                sink.add(invocation.getArgument(0));
                return false;
            });
        } catch (SQLException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /**
     * Generates sequences mixing all three resolution states in random order and length, each
     * resolved case carrying its own distinct server-derived society, so the property is exercised
     * across arbitrary request orderings and many societies.
     */
    @Provide
    Arbitrary<java.util.List<ContextCase>> contextSequences() {
        return contextCase().list().ofMinSize(1).ofMaxSize(20);
    }

    private Arbitrary<ContextCase> contextCase() {
        Arbitrary<ContextState> states = Arbitraries.of(
                ContextState.RESOLVED, ContextState.UNRESOLVED, ContextState.PLATFORM_ADMIN);
        return states.flatMap(state -> {
            if (state == ContextState.RESOLVED) {
                return uuids().map(id -> new ContextCase(state, id));
            }
            return Arbitraries.just(new ContextCase(state, null));
        });
    }

    private static Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()));
    }
}
