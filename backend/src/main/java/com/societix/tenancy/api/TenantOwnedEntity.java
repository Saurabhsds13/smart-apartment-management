package com.societix.tenancy.api;

import java.util.UUID;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Base class for <strong>every tenant-owned JPA entity</strong> in the platform (buildings, flats,
 * resident associations, invoices, bookings, visitor records, and so on from M3 onward). It is the
 * primary, application-layer half of the two-layer tenant-isolation design (ADR-2; Req 1.4, 1.5,
 * 1.6, 1.8); PostgreSQL Row-Level Security (tasks 8.1/8.2) is the independent defense-in-depth
 * second layer.
 *
 * <h2>The {@code societyFilter} convention</h2>
 * This class declares, once and centrally:
 * <ul>
 *   <li>a Hibernate {@link FilterDef} named {@value #FILTER_NAME} with a single {@code UUID}
 *       parameter {@value #SOCIETY_ID_PARAM}; and</li>
 *   <li>a {@link Filter} that restricts rows to {@code society_id = :societyId}.</li>
 * </ul>
 * Because the {@code @Filter} sits on the mapped superclass, it applies automatically to every
 * subclass — a later entity task only has to {@code extends TenantOwnedEntity}; it does not
 * re-declare the filter, the condition, or the column. The filter is <em>defined</em> here but is
 * inert until <em>enabled</em> on the session: {@code SocietyFilterActivator} (tenancy.internal)
 * enables it from the server-derived {@link TenantContext} at the start of every tenant-owned data
 * access, and the tenant-scoped base repository calls that activator so an unscoped query cannot be
 * written by accident (Req 1.5, 1.8).
 *
 * <h2>Why this makes unscoped queries impossible by construction</h2>
 * Once {@value #FILTER_NAME} is enabled on the Hibernate {@code Session}, Hibernate appends the
 * {@code society_id = :societyId} predicate to <em>every</em> select it issues for these entities —
 * {@code findById}, {@code findAll}, derived finders, JPQL, and lazy collection loads alike. A
 * developer cannot "forget" the predicate on a query because they never write it; it is attached by
 * the mapping. When the {@link TenantContext} is unresolved the activator refuses to enable the
 * filter and aborts the operation, so the result is zero tenant-owned rows rather than an unscoped
 * read (Req 1.6).
 *
 * <h2>The {@code society_id} column (Req 1.1)</h2>
 * Every subclass table carries a non-null {@code society_id}. The column is mapped
 * {@code updatable = false}: a row's owning Society is assigned once, at creation, and can never be
 * moved to another tenant by an update. Subclasses (or their services) must set it from the
 * server-derived {@link TenantContext#currentSocietyId()} on create — never from client input
 * (Req 1.2, BR1). The matching database {@code NOT NULL} + foreign-key constraint and the RLS
 * policy (task 8.2) enforce the same invariant at the storage layer.
 *
 * <p><strong>Not for the tenant root.</strong> {@code societies} is the tenant <em>root</em>, not a
 * tenant-owned row, so {@code Society} deliberately does not extend this class and is exempt from
 * {@value #FILTER_NAME} and from the {@code tenant_isolation} RLS policy.
 *
 * <p><strong>Identity is left to subclasses.</strong> This superclass intentionally declares no
 * {@code @Id}: id type and generation strategy (UUIDv7 via a column default, composite keys, etc.)
 * are decisions for each concrete table, mirroring how {@code Society} maps its own id.
 */
@MappedSuperclass
@FilterDef(
        name = TenantOwnedEntity.FILTER_NAME,
        parameters = @ParamDef(name = TenantOwnedEntity.SOCIETY_ID_PARAM, type = UUID.class))
@Filter(
        name = TenantOwnedEntity.FILTER_NAME,
        condition = "society_id = :" + TenantOwnedEntity.SOCIETY_ID_PARAM)
public abstract class TenantOwnedEntity {

    /**
     * Name of the Hibernate filter enabled per request to scope tenant-owned reads. Referenced by
     * {@code SocietyFilterActivator} when it enables the filter on the session.
     */
    public static final String FILTER_NAME = "societyFilter";

    /** Name of the single {@code UUID} parameter carrying the server-derived owning Society id. */
    public static final String SOCIETY_ID_PARAM = "societyId";

    /** Physical name of the tenant-ownership column shared by every tenant-owned table (Req 1.1). */
    public static final String SOCIETY_ID_COLUMN = "society_id";

    /**
     * Owning Society identifier (Req 1.1). Non-null and immutable after creation
     * ({@code updatable = false}) so a row can never be reassigned to a different tenant. Set once,
     * on create, from the server-derived {@link TenantContext#currentSocietyId()} — never from
     * client input.
     */
    @Column(name = SOCIETY_ID_COLUMN, nullable = false, updatable = false)
    private UUID societyId;

    /** JPA requires an accessible no-arg constructor on the superclass. */
    protected TenantOwnedEntity() {
        // for JPA
    }

    /**
     * Creates a tenant-owned entity owned by the given Society.
     *
     * @param societyId the server-derived owning Society id (must not be {@code null}); callers must
     *     pass {@link TenantContext#currentSocietyId()}, never a client-supplied value (Req 1.2, BR1)
     */
    protected TenantOwnedEntity(UUID societyId) {
        this.societyId = societyId;
    }

    /**
     * Returns the owning Society id of this row.
     *
     * @return the owning Society id, or {@code null} only on a transient instance before it has been
     *     assigned on create
     */
    public UUID getSocietyId() {
        return societyId;
    }

    /**
     * Assigns the owning Society exactly once, on creation, for subclasses that use the no-arg
     * constructor (for example JPA builders or framework instantiation). Package-visible to the
     * subclass hierarchy only; it is <strong>not</strong> a public setter and must be fed the
     * server-derived {@link TenantContext#currentSocietyId()}, never client input (Req 1.2, BR1).
     *
     * @param societyId the server-derived owning Society id (must not be {@code null})
     * @throws IllegalStateException if the owning Society has already been assigned
     */
    protected final void assignSociety(UUID societyId) {
        if (this.societyId != null) {
            throw new IllegalStateException(
                    "societyId is immutable once assigned and must not be reassigned");
        }
        this.societyId = societyId;
    }
}
