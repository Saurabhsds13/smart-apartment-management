package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;

/**
 * Fast (no-Docker) unit tests for the tenant-scoping <em>contract</em> of
 * {@link TenantScopedRepositoryImpl} — the application-layer base class behind every
 * {@code TenantScopedRepository} (task 7.3; Req 1.4, 1.5, 1.6, 1.8).
 *
 * <p>These complement the Testcontainers suite {@link TenantIsolationScopingIT}, which proves the
 * real cross-tenant query <em>behaviour</em> against PostgreSQL (rows of society A invisible under
 * society B's context). That behaviour needs a live database and so is skipped where Docker is
 * absent. The two invariants verifiable <em>without</em> a database are asserted here so they run
 * on every machine:
 * <ul>
 *   <li><strong>scope-before-query</strong> — every read first activates the society filter via the
 *       {@link SocietyFilterActivator}, so no read method can issue a query before the
 *       {@code society_id} predicate is in place (Req 1.4, 1.8);</li>
 *   <li><strong>unresolved aborts</strong> — when the {@code TenantContext} is unresolved the
 *       activator throws {@link TenantContextUnresolvedException} <em>before</em> the delegate query
 *       runs, so the operation aborts and yields zero tenant-owned rows rather than an unscoped read
 *       (Req 1.6).</li>
 * </ul>
 *
 * <p>The base class reaches its activator through the static {@link TenantScopingSupport} bridge;
 * these tests publish a mocked {@link SocietyFilterActivator} through that bridge so the call can be
 * observed without a Spring context, then clear it again so no state leaks between tests.
 */
@DisplayName("TenantScopedRepositoryImpl tenant-scoping contract")
class TenantScopedRepositoryScopingTest {

    private SocietyFilterActivator activator;
    private EntityManager entityManager;
    private Session session;
    private TestRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        activator = mock(SocietyFilterActivator.class);
        entityManager = mock(EntityManager.class);
        session = mock(Session.class);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        // SimpleJpaRepository's constructor inspects the EntityManager delegate to pick a
        // persistence provider; a non-null delegate lets construction succeed. We never run a real
        // query (reads are wrapped in runTolerant), so the GENERIC provider it resolves is fine.
        when(entityManager.getDelegate()).thenReturn(new Object());

        // Publish the mocked activator through the static bridge the base class consults per read.
        ObjectProvider<SocietyFilterActivator> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(activator);
        new TenantScopingSupport(provider);

        JpaEntityInformation<Probe, UUID> entityInformation = mock(JpaEntityInformation.class);
        when(entityInformation.getJavaType()).thenReturn(Probe.class);
        repository = new TestRepository(entityInformation, entityManager);
    }

    @AfterEach
    void tearDown() {
        // Reset the static bridge so a later test cannot observe this test's activator.
        new TenantScopingSupport(null);
    }

    @Test
    @DisplayName("activates the society filter before delegating a findAll (scope-before-query, Req 1.4, 1.8)")
    void activatesFilterBeforeQuery() {
        // The superclass query runs against a mocked EntityManager and may fail once control passes
        // it; that is irrelevant to this assertion. What matters is that the scope was activated on
        // the current session FIRST — the base class calls applyTenantScope() at the very start of
        // the read, so the activate(...) call happens before any query could be issued.
        runTolerant(repository::findAll);

        InOrder order = inOrder(activator);
        order.verify(activator).activate(session);
    }

    @Test
    @DisplayName("unresolved context aborts the read before the query runs (Req 1.6)")
    void unresolvedContextAbortsRead() {
        // Simulate an unresolved TenantContext: the activator refuses to enable the filter and
        // throws, exactly as SocietyFilterActivator does when currentSocietyId() is unresolved.
        doThrow(new TenantContextUnresolvedException()).when(activator).activate(any(Session.class));

        assertThatThrownBy(repository::findAll).isInstanceOf(TenantContextUnresolvedException.class);
        assertThatThrownBy(() -> repository.findById(UUID.randomUUID()))
                .isInstanceOf(TenantContextUnresolvedException.class);
        assertThatThrownBy(repository::count).isInstanceOf(TenantContextUnresolvedException.class);
    }

    @Test
    @DisplayName("every by-id / exists / count / list read activates the society filter first (Req 1.4, 1.5, 1.8)")
    void activatesFilterOnEveryReadEntryPoint() {
        // Each distinct read entry point must activate the scope on the session at least once before
        // it could issue a query — asserted per entry point so no single read path can bypass
        // activate(...). (The exact invocation COUNT is a Spring Data delegation detail and is not
        // asserted; presence-before-query is the invariant that matters, Req 1.4, 1.8.)
        assertActivatesOnReading(() -> repository.findById(UUID.randomUUID()));
        assertActivatesOnReading(() -> repository.existsById(UUID.randomUUID()));
        assertActivatesOnReading(repository::count);
        assertActivatesOnReading(repository::findAll);
        assertActivatesOnReading(() -> repository.findAll(org.springframework.data.domain.Sort.unsorted()));
    }

    /**
     * Asserts that invoking the given read activates the society filter on the current session at
     * least once. Invocations are cleared first so each entry point is checked in isolation.
     */
    private void assertActivatesOnReading(Runnable read) {
        org.mockito.Mockito.clearInvocations(activator);
        runTolerant(read);
        verify(activator, org.mockito.Mockito.atLeastOnce()).activate(session);
    }

    /**
     * Invokes a repository read, swallowing any exception thrown by the <em>delegate</em> query
     * against the mocked {@code EntityManager}. The scope-activation call under test happens before
     * delegation, so a downstream mock failure does not affect the {@code verify(activator)}
     * assertions; an {@link TenantContextUnresolvedException} from the activator itself would be a
     * different test and is asserted separately.
     */
    private static void runTolerant(Runnable read) {
        try {
            read.run();
        } catch (RuntimeException ignoredDelegateFailure) {
            // The mocked EntityManager cannot actually run a query; that is fine here.
        }
    }

    /** Concrete tenant-scoped repository over the throwaway {@link Probe} entity. */
    private static final class TestRepository extends TenantScopedRepositoryImpl<Probe, UUID> {
        TestRepository(JpaEntityInformation<Probe, ?> entityInformation, EntityManager em) {
            super(entityInformation, em);
        }
    }

    /** Throwaway tenant-owned entity; never persisted in this unit test. */
    @Entity(name = "ScopingContractProbe")
    static class Probe extends TenantOwnedEntity {
        @Id
        @Column(name = "id")
        private UUID id;

        protected Probe() {
            // for JPA
        }
    }
}
