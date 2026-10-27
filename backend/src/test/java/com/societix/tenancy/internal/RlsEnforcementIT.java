package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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
 * Integration test for the <em>database-enforced</em> tenant-isolation backstop (task 8.3;
 * design.md ADR-2 "tenant isolation — defense-in-depth"; Req 1.5, 1.6; validates the
 * {@code tenant_isolation} RLS policy added by {@code V5__rls_policies.sql}, task 8.2).
 *
 * <h2>What this proves, and why it is independent of the application layer</h2>
 * The PRIMARY tenant-isolation layer is application-side (the Hibernate {@code societyFilter} +
 * base-repository predicate, task 7.3). This test deliberately ignores that layer and exercises the
 * SECOND, database-enforced layer directly: it issues <em>raw SQL</em> over JDBC and shows that
 * PostgreSQL Row-Level Security alone constrains what a connection can read and write, even when no
 * application predicate is present. If a single tenant predicate were ever missed in application
 * code, this backstop must still deny the cross-tenant row — that is exactly what is asserted here.
 *
 * <h2>THE CRUX: you only observe RLS as a NON-SUPERUSER, non-BYPASSRLS, non-owner role</h2>
 * A PostgreSQL <b>superuser</b> and any role with <b>BYPASSRLS</b> SILENTLY IGNORE row-level
 * security policies; a table <b>owner</b> is also exempt UNLESS the table has {@code FORCE ROW
 * LEVEL SECURITY}. Testcontainers' default database user is a <b>superuser</b> — so a test that
 * merely ran queries on the default connection would see every society's rows and would
 * <em>fake a pass</em> while testing nothing. To make this test meaningful it:
 * <ol>
 *   <li>runs Flyway (V1..V5) and all privileged seeding as the default (superuser) connection; and</li>
 *   <li>creates a dedicated runtime-like role {@code societix_app} with
 *       {@code NOSUPERUSER NOBYPASSRLS}, grants it only DML on {@code user_society_memberships},
 *       and performs every <em>observation</em> of RLS on a separate connection authenticated as
 *       that role.</li>
 * </ol>
 * {@code societix_app} is NOT the table owner, so it is subject to RLS by ENABLE alone; the policy
 * additionally carries {@code FORCE ROW LEVEL SECURITY}, so even the owner would be constrained.
 * This mirrors the production posture documented in {@code V1__baseline.sql} and
 * {@code infra/README.md}: the runtime role is non-superuser, not {@code BYPASSRLS}, and does not
 * own the tenant-owned tables.
 *
 * <h2>The policy under test</h2>
 * {@code tenant_isolation} on {@code user_society_memberships} (the only tenant-owned table so far):
 * <pre>
 *   USING      (society_id = current_setting('app.current_society', true)::uuid)
 *   WITH CHECK (society_id = current_setting('app.current_society', true)::uuid)
 * </pre>
 * The GUC {@code app.current_society} is the same per-transaction variable the application sets via
 * {@code SET LOCAL app.current_society = '<uuid>'} (task 8.1, {@link RlsGucTenantScope}).
 *
 * <h2>Scenarios</h2>
 * <ul>
 *   <li><b>Req 1.5 — scoped reads/writes:</b> as {@code societix_app} with the GUC set to society A,
 *       a {@code SELECT} returns only A's rows; a {@code SELECT}/{@code UPDATE}/{@code DELETE}
 *       targeting B's rows sees/affects zero rows (the row is invisible, so the write matches
 *       nothing).</li>
 *   <li><b>Req 1.6 — fail-closed:</b> with the GUC UNSET,
 *       {@code current_setting('app.current_society', true)} is NULL, the predicate is NULL (never
 *       TRUE), and a {@code SELECT} returns ZERO rows.</li>
 *   <li><b>WITH CHECK:</b> an {@code INSERT} (or an {@code UPDATE} relabel) of a row into a society
 *       other than the GUC's is rejected by the policy's {@code WITH CHECK} half — a write cannot
 *       escape the current Tenant_Context.</li>
 *   <li><b>GUC-leak (relates to Req 1.10):</b> {@code SET LOCAL} is transaction-scoped, so a GUC set
 *       in one transaction is gone in the next transaction on the SAME pooled connection —
 *       {@code current_setting(..., true)} reads NULL — proving one request's Tenant_Context does
 *       not leak to the next on a reused connection.</li>
 * </ul>
 *
 * <h2>Local vs CI</h2>
 * Testcontainers needs a Docker daemon. This class is {@code @Testcontainers(disabledWithoutDocker
 * = true)} and tagged {@code "testcontainers"}, so where Docker is absent (e.g. a local box without
 * Docker) JUnit <em>skips</em> the whole class — an honest "not executed" signal, never a fake pass
 * — and CI, which provides Docker, runs every assertion. The {@code *IT} name marks it as an
 * integration test. PostgreSQL is pinned to the 16.x line (design baseline "PostgreSQL 16+"),
 * matching the other integration-test harnesses.
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class RlsEnforcementIT {

    /** The GUC the tenant_isolation policy reads; must match {@link RlsGucTenantScope#GUC_NAME}. */
    private static final String GUC_NAME = RlsGucTenantScope.GUC_NAME;

    /** Dedicated runtime-like role: NON-superuser, NOT BYPASSRLS, NOT the table owner. */
    private static final String APP_ROLE = "societix_app";
    private static final String APP_ROLE_PASSWORD = "societix_app_pw";

    /** Fresh PostgreSQL pinned to the 16.x baseline; Flyway runs the full migration set against it. */
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("societix_rls_test")
                    .withUsername("societix_owner")
                    .withPassword("societix_owner");

    /** Two societies seeded once; their ids drive the cross-tenant assertions. */
    private static UUID societyA;
    private static UUID societyB;

    /**
     * Privileged (superuser) datasource — the Testcontainers default user. Used ONLY for Flyway,
     * role creation, and seeding. RLS is NOT observable here (superuser bypasses it), so this
     * connection is never used to make an RLS assertion.
     */
    private static DataSource ownerDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    /**
     * Runtime-like datasource authenticated as {@link #APP_ROLE} (NOSUPERUSER NOBYPASSRLS). This is
     * the ONLY connection on which RLS behaviour is meaningful, so every RLS assertion runs here.
     */
    private static DataSource appDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(APP_ROLE);
        ds.setPassword(APP_ROLE_PASSWORD);
        return ds;
    }

    @BeforeAll
    static void migrateCreateRoleAndSeed() throws SQLException {
        // 1. Apply every migration (incl. V5__rls_policies.sql) on the fresh container.
        Flyway.configure()
                .dataSource(ownerDataSource())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        try (Connection owner = ownerDataSource().getConnection()) {
            // 2. Create the runtime-like role and grant it only the DML it needs. It is explicitly
            //    NOSUPERUSER + NOBYPASSRLS so it is ALWAYS subject to RLS (the crux of this test),
            //    and it is NOT the owner of user_society_memberships (the Testcontainers default
            //    user owns the Flyway-created tables), so ENABLE RLS alone already constrains it.
            try (Statement st = owner.createStatement()) {
                st.execute(
                        "CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_ROLE_PASSWORD + "' "
                                + "NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE");
                // Needs to resolve the schema and read/write the one tenant-owned table.
                st.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE "
                        + "ON user_society_memberships TO " + APP_ROLE);
                // Reading societies (FK parent, platform-level, no RLS) is handy for assertions.
                st.execute("GRANT SELECT ON societies TO " + APP_ROLE);
            }

            // 3. Seed two societies and one membership row in each, as the privileged owner. (The
            //    owner is subject to FORCE RLS too; we set the GUC per-insert so WITH CHECK passes.)
            societyA = insertSociety(owner, "Society A");
            societyB = insertSociety(owner, "Society B");
            UUID userA = insertUser(owner, "a@example.com");
            UUID userB = insertUser(owner, "b@example.com");
            insertMembership(owner, userA, societyA);
            insertMembership(owner, userB, societyB);
        }
    }

    // --- Req 1.5: reads and writes are constrained to the GUC's society -------------------------

    @Test
    void selectAsAppRoleReturnsOnlyRowsOfTheGucSociety() throws SQLException {
        try (Connection app = appDataSource().getConnection()) {
            app.setAutoCommit(false);
            setGuc(app, societyA);

            // Only society A's membership(s) are visible; B's row is filtered out by RLS even
            // though the query carries no WHERE on society_id.
            assertThat(countAllMemberships(app))
                    .as("with GUC=A, an unfiltered SELECT must return only A's rows (Req 1.5)")
                    .isEqualTo(1);
            assertThat(countMembershipsForSociety(app, societyA)).isEqualTo(1);
            assertThat(countMembershipsForSociety(app, societyB))
                    .as("B's rows are invisible under GUC=A even when explicitly queried (Req 1.5)")
                    .isZero();

            app.rollback();
        }
    }

    @Test
    void updateAndDeleteOfAnotherSocietysRowsAffectZeroRows() throws SQLException {
        try (Connection app = appDataSource().getConnection()) {
            app.setAutoCommit(false);
            setGuc(app, societyA);

            // B's rows are invisible under GUC=A, so the row-selection half of UPDATE/DELETE matches
            // nothing: a cross-tenant write affects ZERO rows (Req 1.5). No exception, no effect.
            try (PreparedStatement upd = app.prepareStatement(
                    "UPDATE user_society_memberships SET status = 'ended' WHERE society_id = ?")) {
                upd.setObject(1, societyB);
                assertThat(upd.executeUpdate())
                        .as("UPDATE targeting another society's rows must affect zero rows (Req 1.5)")
                        .isZero();
            }
            try (PreparedStatement del = app.prepareStatement(
                    "DELETE FROM user_society_memberships WHERE society_id = ?")) {
                del.setObject(1, societyB);
                assertThat(del.executeUpdate())
                        .as("DELETE targeting another society's rows must affect zero rows (Req 1.5)")
                        .isZero();
            }

            app.rollback();
        }

        // B's row is intact (the blocked writes were on an invisible row and we rolled back anyway).
        try (Connection owner = ownerDataSource().getConnection()) {
            assertThat(countMembershipsForSocietyAsOwner(owner, societyB))
                    .as("society B's membership must be untouched by the cross-tenant write attempt")
                    .isEqualTo(1);
        }
    }

    @Test
    void insertIntoAnotherSocietyIsRejectedByWithCheck() throws SQLException {
        try (Connection app = appDataSource().getConnection()) {
            app.setAutoCommit(false);
            setGuc(app, societyA);

            UUID someUser;
            try (Connection owner = ownerDataSource().getConnection()) {
                someUser = insertUser(owner, "c@example.com");
            }

            // WITH CHECK rejects writing a row whose society_id != the GUC: an actor scoped to A
            // cannot insert a row into B. The policy raises rather than silently dropping the row.
            final UUID user = someUser;
            try (PreparedStatement ins = app.prepareStatement(
                    "INSERT INTO user_society_memberships (user_id, society_id, role) "
                            + "VALUES (?, ?, 'OWNER')")) {
                ins.setObject(1, user);
                ins.setObject(2, societyB);
                assertThatThrownBy(ins::executeUpdate)
                        .as("WITH CHECK must reject an INSERT into a society other than the GUC's")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }

            app.rollback();
        }
    }

    // --- Req 1.6: fail-closed when the Tenant_Context (GUC) is unresolved -----------------------

    @Test
    void selectWithUnsetGucReturnsZeroRowsFailClosed() throws SQLException {
        try (Connection app = appDataSource().getConnection()) {
            app.setAutoCommit(false);
            // Deliberately do NOT set the GUC. current_setting('app.current_society', true) is NULL,
            // the predicate society_id = NULL is NULL (never TRUE), so no row is visible.
            assertThat(currentGucIsNull(app))
                    .as("precondition: the GUC must be unset on a fresh transaction")
                    .isTrue();
            assertThat(countAllMemberships(app))
                    .as("an unresolved Tenant_Context must yield zero tenant-owned rows (Req 1.6)")
                    .isZero();

            app.rollback();
        }
    }

    // --- GUC-leak check: SET LOCAL does not leak across transactions on a pooled connection -----

    @Test
    void gucDoesNotLeakAcrossTransactionsOnTheSameConnection() throws SQLException {
        // A single physical connection stands in for a pooled connection reused across two
        // "requests". SET LOCAL is transaction-scoped, so the society bound in transaction #1 is
        // gone in transaction #2 — current_setting reads NULL and the next request sees zero rows
        // until it sets its OWN GUC (defense-in-depth for Req 1.10 / Req 1.6).
        try (Connection app = appDataSource().getConnection()) {
            app.setAutoCommit(false);

            // Transaction #1: bind society A, confirm it is observed, then commit (ends the tx).
            setGuc(app, societyA);
            assertThat(readGuc(app))
                    .as("transaction #1 observes its own GUC")
                    .isEqualTo(societyA.toString());
            assertThat(countAllMemberships(app)).isEqualTo(1);
            app.commit();

            // Transaction #2 on the SAME connection, WITHOUT setting the GUC: it must be unset.
            assertThat(currentGucIsNull(app))
                    .as("SET LOCAL from the previous transaction must NOT leak into the next (Req 1.10)")
                    .isTrue();
            assertThat(countAllMemberships(app))
                    .as("the reused connection is fail-closed until the new request sets its own GUC")
                    .isZero();

            app.rollback();
        }
    }

    // ----------------------------------------------------------------------------------------- //
    // Helpers
    // ----------------------------------------------------------------------------------------- //

    /** {@code SET LOCAL app.current_society = '<uuid>'} — the exact statement the app issues. */
    private static void setGuc(Connection conn, UUID society) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("SET LOCAL " + GUC_NAME + " = '" + society + "'");
        }
    }

    /** Reads the GUC via the two-arg current_setting (missing_ok=true); may be null/empty. */
    private static String readGuc(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT current_setting('" + GUC_NAME + "', true)")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    /** True when the GUC is unset (current_setting returns SQL NULL or an empty string). */
    private static boolean currentGucIsNull(Connection conn) throws SQLException {
        String v = readGuc(conn);
        return v == null || v.isEmpty();
    }

    private static int countAllMemberships(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT count(*) FROM user_society_memberships")) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static int countMembershipsForSociety(Connection conn, UUID society) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM user_society_memberships WHERE society_id = ?")) {
            ps.setObject(1, society);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Counts B's rows from the privileged owner connection with the GUC set to B, so FORCE RLS on
     * the owner does not hide the row we are verifying survived the cross-tenant write attempt.
     */
    private static int countMembershipsForSocietyAsOwner(Connection owner, UUID society)
            throws SQLException {
        owner.setAutoCommit(false);
        try {
            setGuc(owner, society);
            return countMembershipsForSociety(owner, society);
        } finally {
            owner.rollback();
            owner.setAutoCommit(true);
        }
    }

    private static UUID insertSociety(Connection owner, String name) throws SQLException {
        try (PreparedStatement ps = owner.prepareStatement(
                "INSERT INTO societies (name) VALUES (?) RETURNING id")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static UUID insertUser(Connection owner, String email) throws SQLException {
        try (PreparedStatement ps = owner.prepareStatement(
                "INSERT INTO users (email, full_name) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, email);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /**
     * Inserts one membership row as the privileged owner. Because user_society_memberships has
     * FORCE RLS, even the owner must satisfy WITH CHECK, so we bind the GUC to the target society
     * for the duration of this seeding transaction.
     */
    private static void insertMembership(Connection owner, UUID userId, UUID societyId)
            throws SQLException {
        owner.setAutoCommit(false);
        try {
            setGuc(owner, societyId);
            try (PreparedStatement ps = owner.prepareStatement(
                    "INSERT INTO user_society_memberships (user_id, society_id, role) "
                            + "VALUES (?, ?, 'OWNER')")) {
                ps.setObject(1, userId);
                ps.setObject(2, societyId);
                ps.executeUpdate();
            }
            owner.commit();
        } catch (SQLException e) {
            owner.rollback();
            throw e;
        } finally {
            owner.setAutoCommit(true);
        }
    }
}
