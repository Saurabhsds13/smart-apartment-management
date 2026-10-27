package com.societix.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditService;
import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.internal.Society;
import com.societix.tenancy.internal.SocietyRepository;

import jakarta.persistence.EntityManagerFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.LifecycleContext;
import net.jqwik.api.lifecycle.PropagationMode;
import net.jqwik.api.lifecycle.SkipExecutionHook;

/**
 * Testcontainers property-based test for the <strong>audit-atomicity invariant</strong>
 * (task 11.2; design.md Correctness Property 9; Req 16.1, 16.2, 16.3, 16.6).
 *
 * <p><strong>Feature: societix-platform, Property 9: Audit-atomicity invariant</strong> — for all
 * audited financial, security, and administrative operations, the domain state change is persisted
 * if and only if its audit record is persisted (both commit or neither).
 *
 * <h2>How the "iff" is driven</h2>
 * Each generated operation performs, inside a <em>single</em> transaction, a domain state change (a
 * {@link Society} insert — a stand-in for any financial/security/administrative write) <em>and</em>
 * an {@link AuditService#record} call for that change. Because {@code record(...)} uses
 * {@code MANDATORY} propagation it joins that same transaction, so the two share a fate:
 * <ul>
 *   <li><strong>commit</strong> operations let the transaction complete — afterward both the new
 *       Society row <em>and</em> exactly one new audit row exist;</li>
 *   <li><strong>rollback</strong> operations throw after both writes (simulating a later business
 *       step, or the audit/state write itself, failing) — afterward <em>neither</em> the Society
 *       row nor the audit row exists.</li>
 * </ul>
 * Over the whole generated sequence the property asserts the counts move together: the number of
 * committed domain rows equals the number of audit rows, and both equal the number of commit
 * operations — never partially one without the other. This is exactly Property 9's "persisted iff
 * persisted" (Req 16.1-16.3, 16.6).
 *
 * <h2>Local vs CI (honest skip, never a fake pass)</h2>
 * Real atomicity can only be shown against a real transactional database, so this class runs a
 * pinned PostgreSQL 16 container via Testcontainers. Because this is a <em>jqwik</em> class (its
 * checks are {@code @Property} methods), the JUnit-Jupiter {@code @Testcontainers} extension does
 * not drive its lifecycle — so the container is started manually in {@link #startContainer()} and
 * stopped in {@link #stopContainer()}. Where no Docker daemon is present (e.g. a local box without
 * Docker) the container is never started and every property is {@link Assumptions#assumeTrue
 * aborted/skipped} — a truthful "not executed", never a fabricated pass — while CI, which provides
 * Docker, runs every try. The class is tagged {@code "testcontainers"} so a CI pipeline can select
 * or isolate these real-DB checks, mirroring {@link com.societix.tenancy.internal.SocietyRepositoryIT}
 * and {@link com.societix.migration.FlywayMigrationIT}.
 */
@org.junit.jupiter.api.Tag("property")
@org.junit.jupiter.api.Tag("testcontainers")
@net.jqwik.api.Tag("property")
@net.jqwik.api.Tag("testcontainers")
@AddLifecycleHook(value = SkipWithoutDocker.class, propagateTo = PropagationMode.ALL_DESCENDANTS)
class AuditAtomicityIT {

    /** Fresh PostgreSQL pinned to the 16.x baseline; Flyway runs V1 + V2 + V3 against it. */
    private static PostgreSQLContainer<?> postgres;

    private static AnnotationConfigApplicationContext context;
    private static AuditService auditService;
    private static AuditRecordRepository auditRepository;
    private static SocietyRepository societyRepository;
    private static TransactionTemplate transactionTemplate;

    @BeforeContainer
    static void startContainer() {
        // Reached only when SkipWithoutDocker did not skip the container, i.e. Docker is present.
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("societix_audit_atomicity_test")
                .withUsername("societix")
                .withPassword("societix");
        postgres.start();

        System.setProperty("jdbc.url", postgres.getJdbcUrl());
        System.setProperty("jdbc.username", postgres.getUsername());
        System.setProperty("jdbc.password", postgres.getPassword());

        context = new AnnotationConfigApplicationContext(PersistenceConfig.class);
        auditService = context.getBean(AuditService.class);
        auditRepository = context.getBean(AuditRecordRepository.class);
        societyRepository = context.getBean(SocietyRepository.class);
        transactionTemplate =
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterContainer
    static void stopContainer() {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * For all generated sequences of audited operations (a mix of commits and forced rollbacks),
     * after processing: the number of audit rows written equals the number of domain rows written,
     * and both equal the number of commit operations — the state change and its audit record are
     * persisted together or not at all (Req 16.1-16.3, 16.6).
     *
     * <p><strong>Validates: Requirements 16.1, 16.2, 16.3, 16.6</strong>
     */
    @Property(tries = 100)
    void domainAndAuditRowsArePersistedIffTogether(
            @ForAll("operationSequences") List<Operation> ops) {
        // Reached only with Docker present (SkipWithoutDocker skips the whole container otherwise),
        // so the real-DB wiring is guaranteed to be initialized here.

        // Namespace this try with a unique marker so rows retained from other tries do not leak
        // into the counts (the audit table is append-only — committed rows persist across tries).
        String marker = UUID.randomUUID().toString();

        assertThat(countAuditForMarker(marker)).isZero();
        assertThat(countSocietyForMarker(marker)).isZero();

        long expectedCommits = ops.stream().filter(Operation::commit).count();

        for (Operation op : ops) {
            applyOperation(marker, op);
        }

        long auditAfter = countAuditForMarker(marker);
        long societyAfter = countSocietyForMarker(marker);

        assertThat(societyAfter)
                .as("committed domain rows must equal the number of commit operations")
                .isEqualTo(expectedCommits);
        assertThat(auditAfter)
                .as("audit rows must be persisted iff the domain state change was persisted")
                .isEqualTo(societyAfter);
    }

    /**
     * Runs one audited operation inside a single transaction: write the domain row, record the
     * audit entry (joins the same transaction via MANDATORY), then either commit or force a
     * rollback by throwing after both writes.
     */
    private void applyOperation(String marker, Operation op) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Society society = societyRepository.save(new Society(societyName(marker, op)));
                auditService.record(AuditEntry.builder()
                        .action(op.action())
                        .targetType("Society")
                        .societyId(society.getId())
                        .targetId(marker)
                        .afterSummary("{\"name\":\"" + societyName(marker, op) + "\"}")
                        .build());
                if (!op.commit()) {
                    // Simulate a failure after the audit call (e.g. a later business step, or the
                    // write itself failing): the whole transaction — both writes — rolls back.
                    throw new SimulatedFailure();
                }
            });
        } catch (SimulatedFailure expected) {
            // Rollback path: neither the domain row nor its audit row may survive.
        }
    }

    private long countAuditForMarker(String marker) {
        return auditRepository.findAll().stream()
                .filter(r -> marker.equals(r.getTargetId()))
                .count();
    }

    private long countSocietyForMarker(String marker) {
        return societyRepository.findAll().stream()
                .filter(s -> s.getName().startsWith(marker + ":"))
                .count();
    }

    private static String societyName(String marker, Operation op) {
        return marker + ":" + op.action();
    }

    // --- generators ----------------------------------------------------------

    /**
     * A single audited operation: an action code (spanning financial, security, and administrative
     * events, Req 16.1-16.3) and whether its transaction commits or is rolled back.
     */
    record Operation(String action, boolean commit) {
    }

    /** Thrown to force a transaction rollback; carries no state. */
    private static final class SimulatedFailure extends RuntimeException {
        SimulatedFailure() {
            super(null, null, false, false);
        }
    }

    @Provide
    Arbitrary<List<Operation>> operationSequences() {
        Arbitrary<String> actions = Arbitraries.of(
                "PAYMENT_RECORDED", "CREDIT_ALLOCATED", "REFUND_ISSUED", // financial (16.1)
                "ROLE_ASSIGNED", "SESSION_REVOKED",                      // security (16.2)
                "SOCIETY_SUSPENDED", "CONFIG_CHANGED");                  // administrative (16.3)
        Arbitrary<Boolean> commit = Arbitraries.of(true, false);
        Arbitrary<Operation> operation = Combinators.combine(actions, commit).as(Operation::new);
        return operation.list().ofMinSize(1).ofMaxSize(8);
    }

    // --- Spring persistence wiring (manual, so no @SpringBootTest is required) ----------------

    /**
     * Minimal JPA + transaction context backed by the Testcontainers datasource. Flyway owns the
     * schema (V1+V2+V3) just like production; Hibernate never touches DDL. Scanning the audit and
     * tenancy internal packages makes both the real {@link DefaultAuditService} (through its
     * transactional proxy) and the JPA repositories available.
     */
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = {
            "com.societix.audit.internal",
            "com.societix.tenancy.internal"
    })
    @EntityScan(basePackages = {
            "com.societix.audit.internal",
            "com.societix.tenancy.internal"
    })
    static class PersistenceConfig {

        @Bean
        DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url(System.getProperty("jdbc.url"))
                    .username(System.getProperty("jdbc.username"))
                    .password(System.getProperty("jdbc.password"))
                    .build();
        }

        @Bean
        org.flywaydb.core.Flyway flyway(DataSource dataSource) {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                @SuppressWarnings("unused") org.flywaydb.core.Flyway flyway) {
            HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
            adapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");

            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setDataSource(dataSource);
            emf.setJpaVendorAdapter(adapter);
            emf.setPackagesToScan("com.societix.audit.internal", "com.societix.tenancy.internal");
            // Flyway owns DDL; Hibernate must not modify the schema (matches runtime policy).
            emf.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "none");
            return emf;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        TenantContext tenantContext() {
            // The write path under test (record) never consults the tenant context; a stub keeps
            // the context free of request-scope wiring. The read path is covered elsewhere.
            return org.mockito.Mockito.mock(TenantContext.class);
        }

        @Bean
        AuditService auditService(AuditRecordRepository repository, TenantContext tenantContext) {
            return new DefaultAuditService(repository, tenantContext);
        }
    }
}

/**
 * jqwik lifecycle hook that skips {@link AuditAtomicityIT} (container and every property) when no
 * Docker daemon is available. This is the jqwik-engine equivalent of
 * {@code @Testcontainers(disabledWithoutDocker = true)}: because the IT's checks are
 * {@code @Property} methods, the JUnit-Jupiter Testcontainers extension does not run, so the skip
 * decision is made here instead. Skipping (rather than aborting each try) yields a truthful
 * "skipped" result locally without Docker and lets CI, which provides Docker, run the real-DB
 * property in full — never a fabricated pass.
 */
class SkipWithoutDocker implements SkipExecutionHook {

    @Override
    public SkipResult shouldBeSkipped(LifecycleContext context) {
        return AuditAtomicityIT.isDockerAvailable()
                ? SkipResult.doNotSkip()
                : SkipResult.skip("Docker is not available; skipping the real-DB audit-atomicity property");
    }
}
