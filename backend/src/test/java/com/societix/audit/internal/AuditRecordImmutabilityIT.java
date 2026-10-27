package com.societix.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for the <em>database-enforced</em> half of audit immutability (task 11.3;
 * design.md ADR-6 "Audit Immutability"; Req 16.4, BR6).
 *
 * <p>Requirement 16.4 / BR6: once an audit record is stored, no operation may update or delete it.
 * {@code V3__audit_records.sql} enforces this at the database with the
 * {@code trg_audit_records_immutable} BEFORE UPDATE OR DELETE trigger (backed by
 * {@code audit_records_block_mutation()}), complemented operationally by a REVOKE of UPDATE/DELETE
 * on the runtime role. This test proves the trigger backstop against a real PostgreSQL engine:
 *
 * <ol>
 *   <li>a row is inserted into {@code audit_records} (the append-only write path must keep
 *       working);</li>
 *   <li>a direct SQL {@code UPDATE} of that row is rejected — the trigger raises, so the row cannot
 *       be modified;</li>
 *   <li>a direct SQL {@code DELETE} of that row is rejected — the trigger raises, so the row cannot
 *       be removed;</li>
 *   <li>after both failed attempts the original row is still present and unchanged.</li>
 * </ol>
 *
 * <p>The application-layer half of the same guarantee (the entity is write-once and the service
 * exposes no update/delete path) is asserted separately and Docker-free by
 * {@link AuditRecordImmutabilityTest}.
 *
 * <p><b>Why a standalone Flyway + JDBC harness</b> (rather than {@code @SpringBootTest}): the
 * behaviour under test is purely database-side — the trigger fires on any UPDATE/DELETE regardless
 * of who issues it — so driving Flyway directly against a Testcontainers datasource and exercising
 * the trigger over raw JDBC is the lightest, most targeted approach. This mirrors the existing
 * {@link com.societix.migration.FlywayMigrationIT} harness.
 *
 * <p><b>Engine pinning:</b> PostgreSQL is pinned to the 16.x line (design baseline "PostgreSQL
 * 16+"), matching {@code FlywayMigrationIT} and {@code SocietyRepositoryIT}. The migrations rely on
 * the baseline's portable {@code uuid_generate_v7()} / {@code now()} column defaults, so the insert
 * supplies no {@code id} or {@code occurred_at}.
 *
 * <p><b>Local vs CI:</b> Testcontainers needs a Docker daemon. This class is
 * {@code @Testcontainers(disabledWithoutDocker = true)} and tagged {@code "testcontainers"}, so
 * where Docker is absent (e.g. a local box without Docker) JUnit <em>skips</em> the whole class —
 * an honest "not executed" signal, never a fake pass — and CI, which provides Docker, runs every
 * assertion. The {@code *IT} name marks it as an integration test.
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class AuditRecordImmutabilityIT {

    /** Fresh PostgreSQL pinned to the 16.x baseline; Flyway runs the full migration set against it. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_audit_test")
                    .withUsername("societix")
                    .withPassword("societix");

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    /** Apply every migration (incl. V3__audit_records.sql) once on the fresh container. */
    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    /**
     * Inserts one audit record using the append-only write path and returns its generated id. A
     * non-null id confirms INSERT is deliberately left unguarded by the immutability trigger.
     */
    private static String insertAuditRecord(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "INSERT INTO audit_records (action, target_type, target_id) "
                                + "VALUES ('PAYMENT_RECORDED', 'Invoice', 'inv-1') "
                                + "RETURNING id::text")) {
            assertThat(rs.next())
                    .as("append-only INSERT into audit_records must succeed (the trigger guards "
                            + "only UPDATE/DELETE)")
                    .isTrue();
            String id = rs.getString(1);
            assertThat(id).as("inserted audit record must have a generated id").isNotBlank();
            return id;
        }
    }

    @Test
    void updateOfStoredAuditRecordIsRejectedByTheTrigger() throws SQLException {
        try (Connection conn = dataSource().getConnection()) {
            String id = insertAuditRecord(conn);

            try (Statement st = conn.createStatement()) {
                // The trigger raises on BEFORE UPDATE, so the statement fails and the row is
                // never modified (Req 16.4, BR6).
                assertThatThrownBy(() ->
                        st.executeUpdate(
                                "UPDATE audit_records SET action = 'TAMPERED' WHERE id = '" + id + "'"))
                        .as("the DB trigger must reject any UPDATE of a stored audit record")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("append-only");
            }

            assertRowUnchanged(conn, id);
        }
    }

    @Test
    void deleteOfStoredAuditRecordIsRejectedByTheTrigger() throws SQLException {
        try (Connection conn = dataSource().getConnection()) {
            String id = insertAuditRecord(conn);

            try (Statement st = conn.createStatement()) {
                // The trigger raises on BEFORE DELETE, so the statement fails and the row remains
                // (Req 16.4, BR6).
                assertThatThrownBy(() ->
                        st.executeUpdate("DELETE FROM audit_records WHERE id = '" + id + "'"))
                        .as("the DB trigger must reject any DELETE of a stored audit record")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("append-only");
            }

            assertRowUnchanged(conn, id);
        }
    }

    /** Confirms the audit row still exists with its original action after a blocked mutation. */
    private static void assertRowUnchanged(Connection conn, String id) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT action FROM audit_records WHERE id = '" + id + "'")) {
            assertThat(rs.next())
                    .as("the audit record must still exist after a blocked mutation attempt")
                    .isTrue();
            assertThat(rs.getString(1))
                    .as("the audit record's content must be unchanged after a blocked UPDATE")
                    .isEqualTo("PAYMENT_RECORDED");
        }
    }
}
