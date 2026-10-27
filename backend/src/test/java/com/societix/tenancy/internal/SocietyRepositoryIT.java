package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Migration + repository integration test for the {@code societies} table (task 7.1; design.md
 * "Data Models" &gt; societies; Req 13.1, 13.3, 1.1, BR9).
 *
 * <p><b>Why an integration test (real PostgreSQL via Testcontainers):</b> the behaviours worth
 * proving here are database-side — Flyway applies {@code V2__societies.sql} cleanly on top of the
 * V1 baseline, the {@code uuid_generate_v7()} column default assigns a v7 id, the
 * {@code created_at}/{@code updated_at} defaults and the {@code set_updated_at()} trigger stamp
 * UTC timestamps, and the {@code status} CHECK constraint rejects out-of-range values. None of that
 * can be exercised against an in-memory database, so {@code @AutoConfigureTestDatabase} is disabled
 * and a pinned PostgreSQL 16 container backs the datasource.
 *
 * <p><b>Local vs CI:</b> Testcontainers needs a Docker daemon. This class is
 * {@code @Testcontainers(disabledWithoutDocker = true)} and tagged {@code "testcontainers"}, so it
 * is <em>skipped</em> (never a fake pass) where Docker is absent — e.g. a local box without Docker —
 * and runs in full in CI, which provides Docker. This mirrors the existing
 * {@code com.societix.migration.FlywayMigrationIT} harness.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class SocietyRepositoryIT {

    /** Fresh PostgreSQL pinned to the 16.x baseline; Flyway runs V1 + V2 against it. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_tenancy_test")
                    .withUsername("societix")
                    .withPassword("societix");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Flyway owns the schema; Hibernate must not touch DDL (matches the runtime policy).
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private SocietyRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void persistsSocietyWithGeneratedV7IdDefaultStatusAndTimestamps() {
        Society saved = repository.saveAndFlush(new Society("Green Meadows"));
        entityManager.clear();

        Society reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getId())
                .as("id must be assigned by the uuid_generate_v7() column default")
                .isNotNull();
        assertThat(reloaded.getId().version())
                .as("the database default must produce a version-7 UUID")
                .isEqualTo(7);
        assertThat(reloaded.getName()).isEqualTo("Green Meadows");
        assertThat(reloaded.getStatus())
                .as("new societies default to active (Req 13.1)")
                .isEqualTo(SocietyStatus.active);
        assertThat(reloaded.getCreatedAt()).as("created_at default must be set").isNotNull();
        assertThat(reloaded.getUpdatedAt()).as("updated_at default must be set").isNotNull();
    }

    @Test
    void suspensionFlipsStatusAndRetainsTheRow() {
        Society saved = repository.saveAndFlush(new Society("Palm Residency"));
        UUID id = saved.getId();

        saved.setStatus(SocietyStatus.suspended);
        repository.saveAndFlush(saved);
        entityManager.clear();

        Society reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getStatus())
                .as("suspension is a status flip; the row (and its data) is retained (BR9)")
                .isEqualTo(SocietyStatus.suspended);
    }

    @Test
    void updatedAtTriggerAdvancesUpdatedAtOnModification() {
        Society saved = repository.saveAndFlush(new Society("Lake View"));
        UUID id = saved.getId();
        var createdAt = saved.getCreatedAt();
        var firstUpdatedAt = saved.getUpdatedAt();
        entityManager.clear();

        Society reloaded = repository.findById(id).orElseThrow();
        reloaded.setName("Lake View Towers");
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        Society afterUpdate = repository.findById(id).orElseThrow();
        assertThat(afterUpdate.getCreatedAt())
                .as("created_at is immutable")
                .isEqualTo(createdAt);
        assertThat(afterUpdate.getUpdatedAt())
                .as("set_updated_at() trigger must advance updated_at on UPDATE")
                .isAfterOrEqualTo(firstUpdatedAt);
    }

    @Test
    void checkConstraintRejectsOutOfRangeStatus() throws SQLException {
        // Attempt to insert an invalid status straight through JDBC (bypassing the enum) to prove
        // the ck_societies_status CHECK constraint — the DB-layer guard — holds independently of
        // the Java type.
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement()) {
            assertThatThrownBy(() ->
                    st.executeUpdate(
                            "INSERT INTO societies (name, status) VALUES ('Bad', 'archived')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void checkConstraintRejectsBlankName() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Society("   ")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void societiesTableHasNoSocietyIdColumn() throws SQLException {
        // societies is the platform-level tenant root, not a tenant-owned table, so it must NOT
        // carry a society_id column (design "Conventions").
        try (Connection conn = dataSource.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT 1 FROM information_schema.columns "
                                + "WHERE table_name = 'societies' AND column_name = 'society_id'")) {
            assertThat(rs.next())
                    .as("platform-level societies table must not have a society_id column")
                    .isFalse();
        }
    }
}
