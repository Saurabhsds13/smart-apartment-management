package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.hibernate.Session;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.societix.tenancy.api.TenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Integration smoke test proving the <em>mechanism</em> of central tenant scoping against a real
 * PostgreSQL: enabling {@value TenantOwnedEntity#FILTER_NAME} on the Hibernate session constrains a
 * query over a {@link TenantOwnedEntity} subclass to a single society, and leaving it disabled (as
 * when the {@code TenantContext} is unresolved) would otherwise return cross-tenant rows — which is
 * exactly why the base repository always enables it (Req 1.5, 1.6, 1.8).
 *
 * <p>This is intentionally narrow: it exercises the filter directly on a throwaway test entity
 * ({@link ScopingProbe}) so it does not depend on any M3+ domain table. The broad tenant-isolation
 * suite (list/search/export/by-id across the real repositories) is task 7.5.
 *
 * <p><b>Local vs CI.</b> {@code @Testcontainers(disabledWithoutDocker = true)} + {@code @Tag}
 * means this is skipped (never a fake pass) where Docker is absent and runs in full in CI. Hibernate
 * creates the probe table ({@code ddl-auto=create-drop}) since it is a test-only entity with no
 * Flyway migration.
 */
@DataJpaTest(
        // Use only the throwaway probe entity; don't pull real domain entities into this probe test.
        includeFilters =
                @Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {TenantScopingIT.ScopingProbe.class, TenantScopingIT.ProbeConfig.class}))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class TenantScopingIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_scoping_test")
                    .withUsername("societix")
                    .withPassword("societix");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway migrations model the real domain tables; the probe is test-only, so let Hibernate
        // create just the probe table and skip Flyway here.
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private TestEntityManager entityManager;

    @Test
    void enabledSocietyFilterReturnsOnlyInTenantRows() {
        UUID societyA = UUID.randomUUID();
        UUID societyB = UUID.randomUUID();
        entityManager.persist(new ScopingProbe(societyA, "a-1"));
        entityManager.persist(new ScopingProbe(societyA, "a-2"));
        entityManager.persist(new ScopingProbe(societyB, "b-1"));
        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.getEntityManager().unwrap(Session.class);
        session.enableFilter(TenantOwnedEntity.FILTER_NAME)
                .setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, societyA);

        List<ScopingProbe> rows =
                session.createQuery("from ScopingProbe", ScopingProbe.class).getResultList();

        assertThat(rows)
                .as("with the filter enabled for society A, only A's rows are visible (Req 1.5, 1.8)")
                .hasSize(2)
                .allMatch(p -> p.getSocietyId().equals(societyA));
    }

    @Test
    void withoutFilterAllTenantsRowsAreVisible() {
        // Demonstrates WHY the base repository always enables the filter: an unscoped session sees
        // every tenant's rows. The repository never runs in this state for tenant-owned reads — an
        // unresolved context aborts before any query (Req 1.6).
        UUID societyA = UUID.randomUUID();
        UUID societyB = UUID.randomUUID();
        entityManager.persist(new ScopingProbe(societyA, "a-1"));
        entityManager.persist(new ScopingProbe(societyB, "b-1"));
        entityManager.flush();
        entityManager.clear();

        Session session = entityManager.getEntityManager().unwrap(Session.class);
        List<ScopingProbe> rows =
                session.createQuery("from ScopingProbe", ScopingProbe.class).getResultList();

        assertThat(rows).hasSize(2);
    }

    /** Minimal Spring Boot config so {@link DataJpaTest} can bootstrap this test slice. */
    @SpringBootApplication
    static class ProbeConfig {
    }

    /**
     * Throwaway tenant-owned entity used only to exercise the {@link TenantOwnedEntity} filter
     * mapping end-to-end against a real database.
     */
    @Entity(name = "ScopingProbe")
    @Table(name = "scoping_probe")
    static class ScopingProbe extends TenantOwnedEntity {

        @Id
        @GeneratedValue
        @Column(name = "id")
        private Long id;

        @Column(name = "label")
        private String label;

        protected ScopingProbe() {
            // for JPA
        }

        ScopingProbe(UUID societyId, String label) {
            super(societyId);
            this.label = label;
        }

        Long getId() {
            return id;
        }

        String getLabel() {
            return label;
        }
    }
}
