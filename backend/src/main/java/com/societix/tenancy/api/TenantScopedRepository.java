package com.societix.tenancy.api;

import java.io.Serializable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base Spring Data repository that <strong>every tenant-owned repository extends</strong> instead
 * of {@link JpaRepository} directly (ADR-2; Req 1.4, 1.5, 1.8). It is the repository-side half of
 * the central tenant-scoping mechanism: its backing base class (the {@code tenancy.internal}
 * {@code TenantScopedRepositoryImpl}) enables the {@value TenantOwnedEntity#FILTER_NAME} Hibernate
 * filter from the server-derived {@link TenantContext} <em>before</em> each read, so the
 * {@code society_id} predicate is applied centrally and an unscoped query is impossible to write by
 * accident.
 *
 * <h2>Why extend this rather than {@link JpaRepository}</h2>
 * A plain {@link JpaRepository} issues selects with no tenant predicate; a developer would have to
 * remember to add one to every finder, and a single omission is a silent cross-tenant leak. By
 * routing all tenant-owned persistence through this interface, the predicate is attached by the
 * mapping ({@link TenantOwnedEntity}) and activated by the base class on every call — there is no
 * finder, derived query, or {@code findById} that can run unscoped. When the {@link TenantContext}
 * is unresolved the base class aborts the read and the result is zero rows (Req 1.6).
 *
 * <h2>Usage (M3+ tenant-owned entities)</h2>
 * <pre>{@code
 * public interface FlatRepository extends TenantScopedRepository<Flat, UUID> { }
 * }</pre>
 * The entity ({@code Flat}) extends {@link TenantOwnedEntity}; the repository extends this
 * interface. No other wiring is required in the repository itself — the owning module's Spring Data
 * configuration registers {@code TenantScopedRepositoryImpl} as the repository base class so these
 * repositories pick up the filter-activating behaviour. The platform-root
 * {@code SocietyRepository} is deliberately <em>not</em> a {@code TenantScopedRepository}: societies
 * are the tenant root, not tenant-owned, and must not be filtered.
 *
 * <p>Annotated {@link NoRepositoryBean} so Spring Data does not try to instantiate a bean for this
 * intermediate interface; only concrete sub-interfaces become repositories.
 *
 * @param <T> the tenant-owned entity type (a {@link TenantOwnedEntity} subclass)
 * @param <ID> the entity identifier type
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantOwnedEntity, ID extends Serializable>
        extends JpaRepository<T, ID> {
}
