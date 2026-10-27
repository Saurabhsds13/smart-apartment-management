package com.societix.tenancy.internal;

import java.util.UUID;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;
import com.societix.tenancy.api.TenantScope;

import jakarta.persistence.EntityManager;

/**
 * Enables the {@value com.societix.tenancy.api.TenantOwnedEntity#FILTER_NAME} Hibernate filter on
 * the current {@link Session}, parameterised with the server-derived owning Society, so that every
 * subsequent query over {@link TenantOwnedEntity} subclasses is constrained to that Society
 * (ADR-2; Req 1.4, 1.5, 1.8). This is the single place that binds a {@link TenantContext} to a
 * Hibernate session; the tenant-scoped base repository calls it before each query so no caller
 * needs to remember to.
 *
 * <h2>Resolution outcomes</h2>
 * <ul>
 *   <li><strong>Resolved to a Society</strong> — the filter is enabled with
 *       {@code societyId = TenantContext.currentSocietyId()}. Hibernate then appends
 *       {@code society_id = :societyId} to every select for tenant-owned entities.</li>
 *   <li><strong>Unresolved (anonymous)</strong> — {@link TenantContext#currentSocietyId()} throws
 *       {@link TenantContextUnresolvedException}, which propagates out of {@link #activate(Session)}.
 *       The tenant-owned operation therefore aborts and yields zero rows rather than an unscoped
 *       read (Req 1.6). The filter is <em>not</em> enabled, so there is no code path in which a
 *       tenant-owned query runs without the predicate.</li>
 *   <li><strong>Platform_Admin</strong> — {@link TenantContext#currentSocietyId()} also throws
 *       {@link TenantContextUnresolvedException} (platform scope has no single owning Society), so a
 *       Platform_Admin cannot read tenant-owned rows through this ordinary scoped path. Cross-tenant
 *       platform operations run through their own distinct, audited path (Req 1.14).</li>
 * </ul>
 *
 * <h2>Client-supplied Society ids cannot scope queries (Req 1.5, BR1)</h2>
 * The only value ever bound to the filter parameter comes from {@link TenantContext}, which is
 * populated solely from the authenticated session (never from client input, task 7.2). This class
 * exposes no overload that accepts a caller-provided Society id, so there is structurally no way for
 * a request header, parameter, path, or body to widen or redirect the scope.
 *
 * <h2>Both isolation layers are set together (ADR-2 defense-in-depth)</h2>
 * The same {@link #activate(Session)} call that enables the application-layer Hibernate filter also
 * sets the database-layer GUC {@code app.current_society} on the transaction's JDBC connection via
 * {@link TenantScope#applyToSession(java.sql.Connection)}, so the PostgreSQL RLS policies (task 8.2)
 * and the Hibernate filter observe the <em>same</em> server-derived society. Binding both from the
 * one resolved {@link TenantContext}, at the one point, keeps the two layers from ever disagreeing
 * and means neither is enabled without the other (Req 1.5, 1.10).
 */
@Component
public class SocietyFilterActivator {

    private final TenantContext tenantContext;
    private final EntityManager entityManager;
    private final TenantScope tenantScope;

    /**
     * @param tenantContext the request-scoped tenant context (injected as a scoped proxy so the
     *     per-request instance is observed on each call)
     * @param entityManager the shared, transaction-bound {@link EntityManager} proxy; its underlying
     *     {@link Session} is the one carrying the active transaction's queries
     * @param tenantScope the database-layer enforcement primitive that sets the
     *     {@code app.current_society} GUC used by PostgreSQL RLS (ADR-2 defense-in-depth)
     */
    public SocietyFilterActivator(
            TenantContext tenantContext, EntityManager entityManager, TenantScope tenantScope) {
        this.tenantContext = tenantContext;
        this.entityManager = entityManager;
        this.tenantScope = tenantScope;
    }

    /**
     * Enables the society filter on the <em>current</em> session (the one bound to the active
     * {@link EntityManager}). Convenience overload for callers that do not already hold a
     * {@link Session}.
     *
     * @throws TenantContextUnresolvedException if no owning Society is resolved for the request; the
     *     caller must treat this as an abort yielding zero tenant-owned rows (Req 1.6)
     */
    public void activate() {
        activate(entityManager.unwrap(Session.class));
    }

    /**
     * Enables the society filter on the given session for the current request's owning Society.
     *
     * <p>Reading {@link TenantContext#currentSocietyId()} first is deliberate: if the context is
     * unresolved it throws <em>before</em> any filter is enabled or any GUC is set, so the operation
     * aborts cleanly (Req 1.6). When resolved, the filter is (re)enabled idempotently — re-enabling
     * simply refreshes the parameter to the same server-derived value — and the database GUC is
     * re-set for the current transaction via {@link TenantScope#applyToSession(java.sql.Connection)}.
     * Both are therefore safe to call on every query.
     *
     * <p>The GUC is set with {@link Session#doWork(org.hibernate.jdbc.Work)} so it runs on the exact
     * JDBC connection backing this session's transaction — the same connection the subsequent
     * queries use — guaranteeing the RLS policies see the GUC. Using {@code SET LOCAL} inside that
     * transaction means PostgreSQL auto-clears it on commit/rollback (Req 1.10).
     *
     * @param session the Hibernate session to scope (must not be {@code null})
     * @throws TenantContextUnresolvedException if no owning Society is resolved for the request
     */
    public void activate(Session session) {
        // Resolve the server-derived society FIRST. If unresolved/platform-admin this throws, and we
        // deliberately leave both layers unset so the operation aborts (Req 1.6) rather than running
        // unscoped. (applyToSession re-reads the same resolved context for the GUC.)
        UUID societyId = tenantContext.currentSocietyId();
        Filter filter = session.enableFilter(TenantOwnedEntity.FILTER_NAME);
        filter.setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, societyId);
        // Set the database-layer GUC on the transaction's own connection (ADR-2 defense-in-depth).
        session.doWork(tenantScope::applyToSession);
    }
}
