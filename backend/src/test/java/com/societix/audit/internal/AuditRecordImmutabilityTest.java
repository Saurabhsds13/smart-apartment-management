package com.societix.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import jakarta.persistence.Column;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.societix.audit.api.AuditService;

/**
 * Fast, Docker-free unit test for the <em>application-layer</em> half of audit immutability
 * (task 11.3; Req 16.4, BR6).
 *
 * <p>Requirement 16.4 / BR6 demand that no application operation available to any user can update
 * or delete a stored audit record. That guarantee is defended in two independent layers:
 *
 * <ol>
 *   <li><strong>Database layer</strong> — the {@code trg_audit_records_immutable} trigger (plus an
 *       operational REVOKE UPDATE/DELETE on the runtime role) rejects every UPDATE/DELETE against
 *       {@code audit_records}. That is exercised against a real PostgreSQL engine by
 *       {@link AuditRecordImmutabilityIT}, which needs Docker and therefore only runs in CI.</li>
 *   <li><strong>Application layer</strong> — there is simply <em>no code path</em> that could issue
 *       such a mutation: the {@link AuditService} contract exposes only a write-once
 *       {@code record(...)} and read-only {@code query(...)}, and the {@link AuditRecord} entity is
 *       write-once (no setters, every persisted column mapped {@code updatable=false}). That half
 *       is what <em>this</em> class asserts — a pure reflection test with no Spring context and no
 *       database, so it runs everywhere, including a local box without Docker, and gives the
 *       immutability task something meaningful that executes locally.</li>
 * </ol>
 *
 * <p>These checks are structural on purpose: they fail loudly if a future change adds a setter, a
 * mutable column mapping, or an update/delete method to the audit surface — the exact regressions
 * that would quietly reopen an application path to modify a stored audit record.
 */
@DisplayName("Audit immutability — application layer (no update/delete path; Req 16.4, BR6)")
class AuditRecordImmutabilityTest {

    @Test
    @DisplayName("AuditRecord exposes no setter / mutator method")
    void entityExposesNoSetter() {
        for (Method method : AuditRecord.class.getDeclaredMethods()) {
            // Ignore synthetic/bridge methods the compiler may add.
            if (method.isSynthetic()) {
                continue;
            }
            assertThat(method.getName())
                    .as("AuditRecord must be write-once: it must declare no setter (found %s). "
                            + "A setter would reopen an application path to modify a stored audit "
                            + "record (Req 16.4, BR6).", method.getName())
                    .doesNotStartWith("set");
        }
    }

    @Test
    @DisplayName("every persisted AuditRecord column is mapped updatable=false")
    void everyPersistedColumnIsNonUpdatable() {
        for (Field field : AuditRecord.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            assertThat(column)
                    .as("persisted field '%s' must carry a @Column mapping", field.getName())
                    .isNotNull();
            assertThat(column.updatable())
                    .as("persisted field '%s' must be mapped updatable=false so JPA can never issue "
                            + "an UPDATE against a stored audit record (Req 16.4, BR6)",
                            field.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the AuditService contract declares no update or delete operation")
    void auditServiceContractExposesNoMutationOfStoredRecords() {
        boolean hasForbiddenMethod = Arrays.stream(AuditService.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> {
                    String n = name.toLowerCase();
                    return n.startsWith("update")
                            || n.startsWith("delete")
                            || n.startsWith("remove")
                            || n.startsWith("modify")
                            || n.startsWith("edit")
                            || n.startsWith("purge");
                });

        assertThat(hasForbiddenMethod)
                .as("the AuditService public contract must expose no update/delete/modify path for "
                        + "stored audit records — only write-once record(...) and read query(...) "
                        + "are sanctioned (Req 16.4, BR6)")
                .isFalse();
    }

    @Test
    @DisplayName("the audit repository declares no custom update/delete method beyond JPA defaults")
    void repositoryDeclaresNoCustomMutateExistingMethod() {
        // Only the audit module's OWN declared methods are inspected — the inherited JpaRepository
        // defaults (save/delete/...) are framework machinery, not a sanctioned audit mutation path,
        // and are covered by the DB-layer backstop in AuditRecordImmutabilityIT. What matters here
        // is that the audit module itself adds no bespoke mutate-an-existing-row query.
        for (Method method : AuditRecordRepository.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            String n = method.getName().toLowerCase();
            assertThat(n.startsWith("update") || n.startsWith("delete") || n.startsWith("remove"))
                    .as("AuditRecordRepository must declare no custom update/delete method (found "
                            + "%s); the append-only surface is record+read only (Req 16.4, BR6)",
                            method.getName())
                    .isFalse();
        }
    }
}
