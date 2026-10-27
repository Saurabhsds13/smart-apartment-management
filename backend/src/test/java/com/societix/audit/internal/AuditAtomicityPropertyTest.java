package com.societix.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.societix.audit.api.AuditEntry;
import com.societix.audit.api.AuditService;
import com.societix.tenancy.api.TenantContext;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Property-based test for the <strong>audit-atomicity invariant</strong>
 * (task 11.2; design.md Correctness Property 9; Req 16.1, 16.2, 16.3, 16.6).
 *
 * <p><strong>Feature: societix-platform, Property 9: Audit-atomicity invariant</strong> — for all
 * audited financial, security, and administrative operations, the domain state change is persisted
 * if and only if its audit record is persisted: when the audit write (or the surrounding business
 * step) fails the whole operation rolls back and no state change is committed, and when the
 * operation succeeds exactly one corresponding audit record exists.
 *
 * <h2>Why this is split into a fast property and a Testcontainers property</h2>
 * The "iff" invariant is inherently transactional and database-backed, so the most faithful check
 * uses a real PostgreSQL transaction (see {@link AuditAtomicityIT}). That integration test needs
 * Docker and therefore <em>skips</em> where Docker is absent rather than faking a pass.
 *
 * <p>A meaningful part of the invariant is, however, checkable without Docker: the mechanism that
 * makes atomicity possible at all. {@link com.societix.audit.internal.DefaultAuditService#record}
 * is declared {@code @Transactional(propagation = MANDATORY)}, which means it can only ever
 * <em>join</em> an already-open transaction — the caller's, the one that also performs the state
 * change — and can never open its own. The direct consequence, proved here for all generated
 * entries, is that an attempt to record an audit entry with <em>no active transaction</em> is
 * rejected ({@link IllegalTransactionStateException}) and the repository is never touched. That is
 * exactly why an audit record cannot be committed independently of a state change: there is no code
 * path by which {@code record(...)} commits on its own. This property is evaluated against the
 * <em>real Spring transactional proxy</em> (not a bare object), so it exercises the production
 * propagation semantics, just without a database.
 */
@org.junit.jupiter.api.Tag("property")
@net.jqwik.api.Tag("property")
class AuditAtomicityPropertyTest {

    /**
     * Minimal Spring context that wraps {@link DefaultAuditService} in the real transactional proxy
     * so {@code @Transactional(propagation = MANDATORY)} is actually enforced. No datasource is
     * needed: with no active transaction the proxy must reject the call before any repository or
     * transaction-manager interaction, which is the whole point of the property below.
     */
    @Configuration
    @EnableTransactionManagement
    static class ProxyConfig {

        @Bean
        AuditRecordRepository auditRecordRepository() {
            return mock(AuditRecordRepository.class);
        }

        @Bean
        TenantContext tenantContext() {
            return mock(TenantContext.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            // A REAL transaction manager so the production MANDATORY propagation check actually
            // runs. For MANDATORY with no active transaction, AbstractPlatformTransactionManager
            // throws IllegalTransactionStateException before it ever touches the DataSource, so the
            // datasource below is never connected to — it exists only to satisfy construction.
            return new DataSourceTransactionManager(mock(DataSource.class));
        }

        @Bean
        AuditService auditService(AuditRecordRepository repository, TenantContext tenantContext) {
            return new DefaultAuditService(repository, tenantContext);
        }
    }

    private static AnnotationConfigApplicationContext context;
    private static AuditService auditService;
    private static AuditRecordRepository repository;

    @BeforeContainer
    static void startContext() {
        context = new AnnotationConfigApplicationContext(ProxyConfig.class);
        auditService = context.getBean(AuditService.class);
        repository = context.getBean(AuditRecordRepository.class);
    }

    @AfterContainer
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * For all audited entries, invoking {@code record(...)} with no active transaction is rejected
     * by the MANDATORY propagation and persists nothing — proving the audit write can only ride a
     * caller's transaction and never commits on its own (Req 16.6; the mechanism behind 16.1-16.3).
     *
     * <p><strong>Validates: Requirements 16.1, 16.2, 16.3, 16.6</strong>
     */
    @Property(tries = 200)
    void recordWithoutActiveTransactionIsRejectedAndPersistsNothing(
            @ForAll("auditEntries") AuditEntry entry) {
        Mockito.reset(repository);

        assertThatThrownBy(() -> auditService.record(entry))
                .as("record(...) must require an active transaction (MANDATORY propagation), so "
                        + "an audit record can never be committed independently of a state change")
                .isInstanceOf(IllegalTransactionStateException.class);

        // The repository was never reached: nothing was (or could be) written. The MANDATORY
        // propagation check rejects the call before any insert, so there is no path by which an
        // audit record is persisted without the caller's transaction.
        verifyNoInteractions(repository);
    }

    /**
     * Generates audit entries spanning financial, security, and administrative actions (Req
     * 16.1-16.3), including platform-level entries (null society) and entries with/without optional
     * fields, so the property holds across the whole audited-operation input space.
     */
    @Provide
    Arbitrary<AuditEntry> auditEntries() {
        Arbitrary<String> actions = Arbitraries.of(
                // financial (16.1)
                "PAYMENT_RECORDED", "CREDIT_ALLOCATED", "INVOICE_ADJUSTED", "REFUND_ISSUED",
                // security (16.2)
                "ROLE_ASSIGNED", "ROLE_REVOKED", "SESSION_REVOKED", "MFA_ENROLLED",
                // administrative (16.3)
                "SOCIETY_SUSPENDED", "SOCIETY_REACTIVATED", "FLAT_CREATED", "CONFIG_CHANGED");

        Arbitrary<String> targetTypes =
                Arbitraries.of("Invoice", "Receipt", "AccountCredit", "RoleAssignment",
                        "UserSession", "Society", "Flat", "ChargeRule");

        Arbitrary<UUID> societyIds = Arbitraries.oneOf(
                Arbitraries.just((UUID) null), // platform-level event (Req 13.5/16.5)
                uuids());
        Arbitrary<UUID> actorIds = Arbitraries.oneOf(
                Arbitraries.just((UUID) null), // pre-auth/system event
                uuids());
        Arbitrary<String> targetIds = Arbitraries.oneOf(
                Arbitraries.just((String) null),
                uuids().map(UUID::toString));
        Arbitrary<String> beforeSummaries = jsonSummaryOrNull();
        Arbitrary<String> afterSummaries = jsonSummaryOrNull();

        return Combinators.combine(
                        actions, targetTypes, societyIds, actorIds, targetIds,
                        beforeSummaries, afterSummaries)
                .as((action, targetType, societyId, actorId, targetId, before, after) ->
                        AuditEntry.builder()
                                .action(action)
                                .targetType(targetType)
                                .societyId(societyId)
                                .actorUserId(actorId)
                                .targetId(targetId)
                                .beforeSummary(before)
                                .afterSummary(after)
                                .build());
    }

    private static Arbitrary<UUID> uuids() {
        return Arbitraries.randomValue(random -> new UUID(random.nextLong(), random.nextLong()));
    }

    private static Arbitrary<String> jsonSummaryOrNull() {
        Arbitrary<String> json = Arbitraries.integers().between(0, 1_000_000)
                .map(amount -> "{\"amountPaisa\":" + amount + "}");
        return Arbitraries.oneOf(Arbitraries.just((String) null), json);
    }
}
