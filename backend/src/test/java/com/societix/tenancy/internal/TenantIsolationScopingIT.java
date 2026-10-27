package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;
import com.societix.tenancy.api.TenantScope;
import com.societix.tenancy.api.TenantScopedRepository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Broad tenant-isolation suite for the application-layer scoping mechanism (task 7.5; design.md
 * ADR-2 "tenant isolation — primary layer"; Req 1.4, 1.5, 1.6, 1.8, BR1).
 *
 * <p>Where {@link TenantScopingIT} is a narrow smoke test that enables the Hibernate filter
 * <em>by hand</em> on a session, this suite drives the <strong>real production path</strong>
 * end to end against a live PostgreSQL: a concrete {@link TenantScopedRepository} backed by the
 * actual {@link TenantScopedRepositoryImpl} base class, which calls the actual
 * {@link SocietyFilterActivator} (resolving the server-derived society from {@link TenantContext})
 * before every read. Nothing in the test re-enables the {@code societyFilter} itself — the
 * repository does, exactly as it would in production — so what is proven here is the behaviour a
 * caller actually gets:
 * <ul>
 *   <li><strong>cross-tenant list / search / count return only in-tenant rows</strong> — under
 *       society A's context, {@code findAll}, example-based search, and {@code count} see only A's
 *       rows, never B's (Req 1.4, 1.8);</li>
 *   <li><strong>by-id of another society's row is not-found</strong> — {@code findById} /
 *       {@code existsById} of a society B row under A's context returns empty / false, the response
 *       indistinguishable from a genuinely nonexistent id (Req 1.5, 1.7);</li>
 *   <li><strong>unresolved context aborts with zero rows</strong> — with no resolved society, every
 *       read aborts (propagating {@link TenantContextUnresolvedException}) <em>before</em> any query
 *       runs, so the operation yields zero tenant-owned rows rather than an unscoped read
 *       (Req 1.6);</li>
 *   <li><strong>the server-derived society is the only scoping input</strong> — the scope is read
 *       solely from {@link TenantContext}; switching that context is the <em>only</em> way to change
 *       what a query returns, and there is structurally no caller-supplied society id a client could
 *       provide to widen or redirect scope (Req 1.5, BR1).</li>
 * </ul>
 *
 * <h2>Scope of this suite vs the RLS suite (task 8.3)</h2>
 * This is the <em>application-layer</em> isolation suite: it exercises the Hibernate
 * {@code societyFilter} + base-repository predicate. The database-layer Row-Level Security backstop
 * (the {@code app.current_society} GUC and the {@code tenant_isolation} policy) is verified
 * independently in {@link RlsGucTenantScopeTest} and the task 8.3 RLS integration tests. To keep
 * this suite focused on the application layer, it uses a throwaway tenant-owned entity
 * ({@link IsolationProbe}) with no Flyway migration and therefore no RLS policy, and stubs
 * {@link TenantScope} as a no-op; the filter is thus the <em>sole</em> thing constraining results,
 * so any leak would be attributable to the application layer rather than masked by RLS.
 *
 * <h2>Local vs CI (honest skip, never a fake pass)</h2>
 * True cross-tenant behaviour needs a real database with the Hibernate filter active, so a pinned
 * PostgreSQL 16 container backs the datasource. The class is
 * {@code @Testcontainers(disabledWithoutDocker = true)} and tagged {@code "testcontainers"}: where
 * no Docker daemon is present (e.g. a local box without Docker) it is <em>skipped</em> — never a
 * fabricated pass — and it runs in full in CI, which provides Docker. The no-Docker portions of the
 * contract (scope-before-query; unresolved aborts) are additionally covered as fast unit tests in
 * {@link TenantScopedRepositoryScopingTest}, which run on every machine.
 */
@DataJpaTest(
        includeFilters =
                @Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            TenantIsolationScopingIT.IsolationProbe.class,
                            TenantIsolationScopingIT.ScopingTestConfig.class,
                            TenantIsolationScopingIT.IsolationProbeRepository.class
                        }))
@EnableJpaRepositories(
        considerNestedRepositories = true,
        repositoryBaseClass = TenantScopedRepositoryImpl.class,
        basePackageClasses = TenantIsolationScopingIT.IsolationProbeRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class TenantIsolationScopingIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_isolation_test")
                    .withUsername("societix")
                    .withPassword("societix");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The probe is a test-only tenant-owned entity with no Flyway migration; let Hibernate
        // create just its table and skip Flyway (the real domain schema is irrelevant here).
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    private static final UUID SOCIETY_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SOCIETY_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Autowired private TestEntityManager entityManager;
    @Autowired private IsolationProbeRepository repository;
    @Autowired private MutableTenantContext tenantContext;

    private UUID aRowId;
    private UUID bRowId;

    @BeforeEach
    void seedTwoSocieties() {
        // Seed via the TestEntityManager (NOT the scoped repository), so both societies' rows are
        // written regardless of the current context — the scoped repository is the thing under test
        // on the READ side.
        tenantContext.clear();
        IsolationProbe a1 = entityManager.persistFlushFind(new IsolationProbe(SOCIETY_A, "a-1"));
        IsolationProbe a2 = entityManager.persistFlushFind(new IsolationProbe(SOCIETY_A, "a-2"));
        IsolationProbe b1 = entityManager.persistFlushFind(new IsolationProbe(SOCIETY_B, "b-1"));
        aRowId = a1.getId();
        bRowId = b1.getId();
        // Detach so reads below hit the database (and therefore the filter), not the 1st-level cache.
        entityManager.clear();
        assertThat(a2.getId()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("list / count under society A see only A's rows, never B's (Req 1.4, 1.8)")
    void listAndCountReturnOnlyInTenantRows() {
        tenantContext.resolveTo(SOCIETY_A);

        List<IsolationProbe> rows = repository.findAll();

        assertThat(rows)
                .as("findAll under A returns exactly A's two rows and none of B's (Req 1.8)")
                .hasSize(2)
                .allMatch(p -> p.getSocietyId().equals(SOCIETY_A));
        assertThat(repository.count())
                .as("count is scoped to A as well (Req 1.4)")
                .isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("example-based search under society A excludes B's rows (Req 1.8)")
    void exampleSearchReturnsOnlyInTenantRows() {
        tenantContext.resolveTo(SOCIETY_B);

        // Probe by a field common to all rows would, unscoped, match every society; scoping must
        // restrict it to the current context.
        List<IsolationProbe> rows = repository.findAll(Example.of(new IsolationProbe()));

        assertThat(rows)
                .as("search under B returns only B's single row (Req 1.8)")
                .hasSize(1)
                .allMatch(p -> p.getSocietyId().equals(SOCIETY_B));
    }

    @Test
    @Transactional
    @DisplayName("findById of another society's row is not-found under A (Req 1.5, 1.7)")
    void byIdOfForeignSocietyRowIsNotFound() {
        tenantContext.resolveTo(SOCIETY_A);

        // The B row genuinely exists (seeded above), but under A's context it must be invisible and
        // indistinguishable from a nonexistent id.
        Optional<IsolationProbe> foreign = repository.findById(bRowId);
        assertThat(foreign)
                .as("a society B row is not-found under society A's context (Req 1.5, 1.7)")
                .isEmpty();
        assertThat(repository.existsById(bRowId))
                .as("existsById must not disclose the foreign row either (Req 1.7)")
                .isFalse();

        // A genuinely nonexistent id behaves identically — the two cases are indistinguishable.
        assertThat(repository.findById(UUID.randomUUID()))
                .as("nonexistent id is also empty — same response as the cross-tenant case (Req 1.7)")
                .isEmpty();

        // The in-tenant row IS visible, confirming the filter is not simply hiding everything.
        assertThat(repository.findById(aRowId))
                .as("the in-tenant row is visible under its own society")
                .isPresent();
    }

    @Test
    @Transactional
    @DisplayName("switching context is the ONLY way to change results; scope is server-derived (Req 1.5, BR1)")
    void serverDerivedContextIsTheSoleScopingInput() {
        // Same query, two contexts, disjoint results — driven ENTIRELY by the server-side context.
        tenantContext.resolveTo(SOCIETY_A);
        List<IsolationProbe> underA = repository.findAll();

        tenantContext.resolveTo(SOCIETY_B);
        List<IsolationProbe> underB = repository.findAll();

        assertThat(underA).allMatch(p -> p.getSocietyId().equals(SOCIETY_A));
        assertThat(underB).allMatch(p -> p.getSocietyId().equals(SOCIETY_B));
        assertThat(underA).as("A and B see disjoint row sets").noneMatch(underB::contains);

        // There is no API on the repository, the activator, or TenantContext that accepts a
        // caller-supplied society id: the only lever is the server-derived context switched above.
        // A client cannot therefore widen or redirect scope by supplying an id (Req 1.5, BR1).
    }

    @Test
    @Transactional
    @DisplayName("unresolved context aborts every read and yields zero rows (Req 1.6)")
    void unresolvedContextAbortsAndReturnsZeroRows() {
        tenantContext.clear(); // no society resolved — e.g. an anonymous/background path

        // Each read aborts by propagating the unresolved exception BEFORE any query runs, so no
        // unscoped read can leak cross-tenant rows; the operation yields zero tenant-owned records.
        assertThatThrownBy(repository::findAll)
                .as("list aborts when the context is unresolved (Req 1.6)")
                .isInstanceOf(TenantContextUnresolvedException.class);
        assertThatThrownBy(repository::count)
                .isInstanceOf(TenantContextUnresolvedException.class);
        assertThatThrownBy(() -> repository.findById(aRowId))
                .isInstanceOf(TenantContextUnresolvedException.class);
        assertThatThrownBy(() -> repository.findAll(Example.of(new IsolationProbe())))
                .isInstanceOf(TenantContextUnresolvedException.class);
    }

    @Test
    @Transactional
    @DisplayName("platform-admin (no single owning society) is also aborted on this ordinary path (Req 1.6, 1.14)")
    void platformAdminHasNoImplicitCrossTenantViewOnOrdinaryPath() {
        tenantContext.resolvePlatformAdmin();

        // A Platform_Admin has no single owning society, so currentSocietyId() throws just as for an
        // unresolved request: the ordinary tenant-scoped repository gives no cross-tenant view. Any
        // genuine cross-society access must go through the distinct, audited path (Req 1.14).
        assertThatThrownBy(repository::findAll)
                .isInstanceOf(TenantContextUnresolvedException.class);
    }

    // ------------------------------------------------------------------------
    // Test wiring
    // ------------------------------------------------------------------------

    /** Minimal Spring Boot config so {@link DataJpaTest} can bootstrap this slice. */
    @SpringBootApplication
    static class ScopingTestConfig {

        /**
         * A directly controllable {@link TenantContext}, standing in for the request-scoped one. It
         * lets a test "log in" as society A, society B, platform-admin, or no one — the only inputs
         * the production scoping path reads — so the suite can switch the server-derived scope
         * without an HTTP request. Marked {@link Primary} so it is injected wherever a
         * {@link TenantContext} is required (the {@link SocietyFilterActivator}, the
         * {@link TenantScope}).
         */
        @Bean
        @Primary
        MutableTenantContext tenantContext() {
            return new MutableTenantContext();
        }

        /**
         * No-op {@link TenantScope}: the DB-layer GUC / RLS backstop is out of scope for this
         * application-layer suite (and the probe table has no RLS policy), so binding the GUC would
         * be inert here. Keeping it a no-op makes the Hibernate {@code societyFilter} the <em>sole</em>
         * constraint on results, so a leak could only come from the application layer under test.
         * The real {@link RlsGucTenantScope} is covered by its own tests and task 8.3.
         */
        @Bean
        @Primary
        TenantScope tenantScope() {
            return new TenantScope() {
                @Override
                public void applyToSession(Connection connection) {
                    // no-op: see Javadoc — RLS GUC is exercised elsewhere.
                }

                @Override
                public void clearOnReturn(Connection connection) {
                    // no-op
                }
            };
        }
    }

    /**
     * Test double for {@link TenantContext} with public mutators the suite uses to switch the
     * server-derived scope. Mirrors the three resolution states of the production
     * {@code RequestScopedTenantContext}: society-resolved, platform-admin, and unresolved. It is
     * deliberately simple state — no request scope — because the point of the suite is to vary the
     * <em>context</em> and observe the repository behaviour the production activator produces from
     * it.
     */
    static final class MutableTenantContext implements TenantContext {

        private enum State {
            UNRESOLVED,
            SOCIETY,
            PLATFORM_ADMIN
        }

        private final AtomicReference<State> state = new AtomicReference<>(State.UNRESOLVED);
        private volatile UUID societyId;

        void resolveTo(UUID id) {
            this.societyId = id;
            this.state.set(State.SOCIETY);
        }

        void resolvePlatformAdmin() {
            this.societyId = null;
            this.state.set(State.PLATFORM_ADMIN);
        }

        void clear() {
            this.societyId = null;
            this.state.set(State.UNRESOLVED);
        }

        @Override
        public UUID currentSocietyId() {
            if (state.get() != State.SOCIETY) {
                throw new TenantContextUnresolvedException();
            }
            return societyId;
        }

        @Override
        public boolean isPlatformAdmin() {
            return state.get() == State.PLATFORM_ADMIN;
        }

        @Override
        public boolean isResolved() {
            return state.get() != State.UNRESOLVED;
        }
    }

    /** Concrete tenant-scoped repository over the probe, backed by the real base class. */
    interface IsolationProbeRepository extends TenantScopedRepository<IsolationProbe, UUID> {
    }

    /**
     * Throwaway tenant-owned entity used to exercise the real scoping path against a live database
     * without depending on any M3+ domain table. Extends {@link TenantOwnedEntity}, so it carries
     * the shared {@code society_id} column and the {@code societyFilter} mapping automatically.
     */
    @Entity(name = "IsolationProbe")
    @Table(name = "isolation_probe")
    static class IsolationProbe extends TenantOwnedEntity {

        @Id
        @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
        @Column(name = "id")
        private UUID id;

        @Column(name = "label")
        private String label;

        protected IsolationProbe() {
            // for JPA and example-probe construction
        }

        IsolationProbe(UUID societyId, String label) {
            super(societyId);
            this.label = label;
        }

        UUID getId() {
            return id;
        }

        String getLabel() {
            return label;
        }
    }
}
