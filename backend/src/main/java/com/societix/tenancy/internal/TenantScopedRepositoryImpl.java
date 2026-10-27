package com.societix.tenancy.internal;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import org.hibernate.Session;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;
import com.societix.tenancy.api.TenantScopedRepository;

import jakarta.persistence.EntityManager;

/**
 * Spring Data base class behind every {@link TenantScopedRepository}. It wraps the standard
 * {@link SimpleJpaRepository} and enables the {@value TenantOwnedEntity#FILTER_NAME} Hibernate
 * filter from the server-derived {@code TenantContext} <strong>before each read</strong>, so the
 * {@code society_id} predicate is applied centrally and an unscoped tenant-owned query is impossible
 * by construction (ADR-2; Req 1.4, 1.5, 1.8).
 *
 * <h2>How it is wired</h2>
 * Spring Data instantiates this class once per tenant-owned repository, passing the entity
 * information and the shared {@link EntityManager}. The owning module's Spring Data configuration
 * registers it via {@code @EnableJpaRepositories(repositoryBaseClass = TenantScopedRepositoryImpl.class)}
 * for its tenant-owned repositories. The request-scoped filter activation is delegated to
 * {@link TenantScopingSupport}, a Spring-managed holder that exposes the request-scoped
 * {@link SocietyFilterActivator} to this framework-instantiated (non-bean) base class.
 *
 * <h2>Read path (Req 1.4, 1.5, 1.6, 1.8)</h2>
 * Every read method activates the filter first:
 * <ul>
 *   <li>when the context is resolved to a Society, Hibernate appends {@code society_id = :societyId}
 *       to the query, so only in-tenant rows are returned (Req 1.5, 1.8);</li>
 *   <li>when the context is unresolved (anonymous) or platform-admin, activation throws
 *       {@link TenantContextUnresolvedException} <em>before</em> the query runs, aborting the
 *       operation so it yields zero tenant-owned rows rather than an unscoped read (Req 1.6).</li>
 * </ul>
 * The filter parameter is only ever the server-derived society; no client-supplied id can reach it
 * (Req 1.5, BR1). Because the predicate is attached by the mapping and activated here, a concrete
 * repository cannot define a finder — derived, {@code findById}, {@code findAll}, or example-based —
 * that escapes the predicate.
 *
 * @param <T> the tenant-owned entity type
 * @param <ID> the identifier type
 */
public class TenantScopedRepositoryImpl<T extends TenantOwnedEntity, ID extends Serializable>
        extends SimpleJpaRepository<T, ID> implements TenantScopedRepository<T, ID> {

    private final EntityManager entityManager;

    /**
     * @param entityInformation Spring Data entity metadata for {@code T}
     * @param entityManager the shared, transaction-bound entity manager whose session the filter is
     *     enabled on
     */
    public TenantScopedRepositoryImpl(
            JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    /**
     * Enables the society filter on the current session for the request's owning Society. Called at
     * the start of every read. Aborts (by propagating {@link TenantContextUnresolvedException}) when
     * no Society is resolved, so the read yields zero rows (Req 1.6).
     */
    private void applyTenantScope() {
        TenantScopingSupport.filterActivator().activate(entityManager.unwrap(Session.class));
    }

    // ------------------------------------------------------------------
    // Reads — scope first, then delegate.
    // ------------------------------------------------------------------

    @Override
    public Optional<T> findById(ID id) {
        applyTenantScope();
        return super.findById(id);
    }

    @Override
    public boolean existsById(ID id) {
        applyTenantScope();
        return super.existsById(id);
    }

    @Override
    public T getReferenceById(ID id) {
        applyTenantScope();
        return super.getReferenceById(id);
    }

    @Override
    public List<T> findAll() {
        applyTenantScope();
        return super.findAll();
    }

    @Override
    public List<T> findAll(Sort sort) {
        applyTenantScope();
        return super.findAll(sort);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        applyTenantScope();
        return super.findAll(pageable);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        applyTenantScope();
        return super.findAllById(ids);
    }

    @Override
    public long count() {
        applyTenantScope();
        return super.count();
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        applyTenantScope();
        return super.findOne(example);
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        applyTenantScope();
        return super.findAll(example);
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        applyTenantScope();
        return super.findAll(example, sort);
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        applyTenantScope();
        return super.findAll(example, pageable);
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        applyTenantScope();
        return super.count(example);
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        applyTenantScope();
        return super.exists(example);
    }

    // ------------------------------------------------------------------
    // Writes — scope so updates/deletes cannot touch another tenant's rows.
    // The filter also constrains the row set a bulk delete can reach, and the
    // NOT NULL society_id + RLS layer (task 8.2) backstop persist/merge.
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void deleteById(ID id) {
        applyTenantScope();
        super.deleteById(id);
    }

    @Override
    @Transactional
    public void delete(T entity) {
        applyTenantScope();
        super.delete(entity);
    }

    @Override
    @Transactional
    public void deleteAll() {
        applyTenantScope();
        super.deleteAll();
    }
}
