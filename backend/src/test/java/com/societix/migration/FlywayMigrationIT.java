package com.societix.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Migration test harness (task 1.4; design.md "Testing Strategy > Migration tests", Req A1, A3).
 *
 * <p>Verifies that the Flyway migrations in {@code classpath:db/migration} apply cleanly on a
 * <em>fresh</em> PostgreSQL container that mirrors the production engine family. This is the
 * backstop the design's quality gate requires: "Flyway migrations apply cleanly on a fresh
 * database" (design.md "Quality Gates" item 4).
 *
 * <p><b>Why a standalone Flyway test rather than {@code @SpringBootTest}:</b> this task only needs
 * to prove the SQL scripts apply against a real engine; it does not need the application context,
 * JPA, or Spring Security. Driving Flyway directly against a Testcontainers datasource is the
 * lighter, faster, and more targeted approach — it isolates "do the migrations apply?" from any
 * unrelated context-wiring failure. The heavier {@code @SpringBootTest + @DynamicPropertySource}
 * style is reserved for repository/RLS integration tests in later milestones.
 *
 * <p><b>Engine pinning:</b> the PostgreSQL image is pinned to a specific 16.x tag (design baseline
 * is "PostgreSQL 16+"). V1__baseline.sql defines a portable {@code uuid_generate_v7()} precisely
 * because the built-in {@code uuidv7()} only exists on PG 18+; pinning 16 keeps this harness honest
 * about the minimum supported engine.
 *
 * <p><b>Docker requirement:</b> Testcontainers needs a working Docker daemon. This class is annotated
 * {@code @Testcontainers(disabledWithoutDocker = true)} so that, when no Docker environment is
 * present, JUnit <em>skips</em> the whole class rather than failing it — an honest "not executed"
 * signal, never a fake pass. CI (which provides Docker) runs the assertions in full. The class is
 * also tagged {@code "migration"} and {@code "testcontainers"} so a CI pipeline can select or
 * isolate these real-DB tests, and the {@code *IT} name marks it as an integration test.
 */
@Tag("migration")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationIT {

    /**
     * Fresh, disposable PostgreSQL engine pinned to the 16.x line (design baseline "PostgreSQL 16+").
     * A new container (hence an empty database) is created for the test class, guaranteeing the
     * migrations run against a pristine schema with no prior Flyway history.
     */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_migration_test")
                    .withUsername("societix_migrator")
                    .withPassword("societix_migrator");

    private static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                // Mirror application.yml: scripts live on the classpath under db/migration.
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                // Match the runtime policy: V1 is the true baseline, nothing is auto-baselined,
                // and the applied history is validated against the scripts on disk.
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }

    @Test
    void migrationsApplyCleanlyOnFreshDatabase() throws SQLException {
        DataSource dataSource = dataSource();
        Flyway flyway = flyway(dataSource);

        // --- Act: apply every migration against the pristine database. --------------------------
        MigrateResult result = flyway.migrate();

        // --- Assert: the run succeeded and reported the expected applied migrations. ------------
        assertThat(result.success).as("Flyway.migrate() must report success").isTrue();
        assertThat(result.migrationsExecuted)
                .as("at least the V1 baseline must be applied on a fresh DB")
                .isGreaterThanOrEqualTo(1);

        // Every applied migration must be in the SUCCESS state (none pending/failed/out-of-order).
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).as("there must be at least one applied migration").isNotEmpty();
        assertThat(applied)
                .allSatisfy(info -> assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));

        // The baseline version V1 must be present among the applied migrations.
        assertThat(applied)
                .anySatisfy(info ->
                        assertThat(info.getVersion().getVersion()).isEqualTo("1"));

        // The current (latest applied) version equals the highest script version; re-running
        // reports a no-op, proving idempotency of the applied history.
        MigrationInfo current = flyway.info().current();
        assertThat(current).as("there must be a current applied migration").isNotNull();
        assertThat(current.getState()).isEqualTo(MigrationState.SUCCESS);

        MigrateResult rerun = flyway.migrate();
        assertThat(rerun.migrationsExecuted)
                .as("re-running migrate on an up-to-date DB applies nothing")
                .isZero();
    }

    @Test
    void baselineCreatesExpectedExtensionsFunctionsAndTimezone() throws SQLException {
        DataSource dataSource = dataSource();
        flyway(dataSource).migrate();

        try (Connection conn = dataSource.getConnection()) {
            // 1. Required extensions from V1__baseline.sql (pgcrypto + btree_gist).
            assertExtensionInstalled(conn, "pgcrypto");
            assertExtensionInstalled(conn, "btree_gist");

            // 2. Shared helper functions from the baseline.
            assertFunctionExists(conn, "uuid_generate_v7");
            assertFunctionExists(conn, "set_updated_at");

            // 3. uuid_generate_v7() actually produces a syntactically valid, version-7 UUID.
            assertGeneratesVersion7Uuid(conn);

            // 4. The database default time zone was pinned to UTC (Req 23.1).
            assertDatabaseTimezoneIsUtc(conn);
        }
    }

    private static void assertExtensionInstalled(Connection conn, String extensionName)
            throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT 1 FROM pg_extension WHERE extname = '" + extensionName + "'")) {
            assertThat(rs.next())
                    .as("extension '%s' must be installed by the baseline migration", extensionName)
                    .isTrue();
        }
    }

    private static void assertFunctionExists(Connection conn, String functionName)
            throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT 1 FROM pg_proc WHERE proname = '" + functionName + "'")) {
            assertThat(rs.next())
                    .as("function '%s()' must be created by the baseline migration", functionName)
                    .isTrue();
        }
    }

    private static void assertGeneratesVersion7Uuid(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT uuid_generate_v7()::text")) {
            assertThat(rs.next()).isTrue();
            String uuidText = rs.getString(1);

            // Parseable as a UUID (correct textual shape).
            UUID parsed = UUID.fromString(uuidText);

            // RFC 9562: the version nibble (first nibble of the 3rd group) must be 7.
            assertThat(parsed.version())
                    .as("uuid_generate_v7() must produce a version-7 UUID, got %s", uuidText)
                    .isEqualTo(7);

            // RFC 9562 variant bits (10xx) -> java.util.UUID variant == 2.
            assertThat(parsed.variant())
                    .as("uuid_generate_v7() must set the RFC 4122/9562 variant, got %s", uuidText)
                    .isEqualTo(2);
        }
    }

    private static void assertDatabaseTimezoneIsUtc(Connection conn) throws SQLException {
        // ALTER DATABASE ... SET timezone applies to NEW sessions; this connection was opened
        // after the migration committed, so SHOW timezone reflects the pinned value.
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SHOW timezone")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                    .as("baseline must pin the database default time zone to UTC (Req 23.1)")
                    .isEqualTo("UTC");
        }
    }
}
