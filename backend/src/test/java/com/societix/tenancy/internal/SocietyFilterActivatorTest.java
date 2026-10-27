package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.societix.tenancy.api.TenantContext;
import com.societix.tenancy.api.TenantContextUnresolvedException;
import com.societix.tenancy.api.TenantOwnedEntity;
import com.societix.tenancy.api.TenantScope;

import jakarta.persistence.EntityManager;

/**
 * Focused unit tests for {@link SocietyFilterActivator} (Req 1.4, 1.5, 1.6, 1.8, BR1).
 *
 * <p>The activator is driven with a mocked {@link TenantContext} and a mocked Hibernate
 * {@link Session}, so the behaviour under test is the activator's own logic: it enables the
 * {@value TenantOwnedEntity#FILTER_NAME} filter with the server-derived society when resolved, and
 * aborts (never enabling the filter) when the context is unresolved or platform-admin.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SocietyFilterActivator")
class SocietyFilterActivatorTest {

    @Mock private TenantContext tenantContext;
    @Mock private EntityManager entityManager;
    @Mock private TenantScope tenantScope;
    @Mock private Session session;
    @Mock private Filter filter;

    @Test
    @DisplayName("enables societyFilter with the server-derived society id when resolved (Req 1.5, 1.8)")
    void enablesFilterWithResolvedSociety() {
        UUID societyId = UUID.randomUUID();
        when(tenantContext.currentSocietyId()).thenReturn(societyId);
        when(session.enableFilter(TenantOwnedEntity.FILTER_NAME)).thenReturn(filter);

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);
        activator.activate(session);

        verify(session).enableFilter(TenantOwnedEntity.FILTER_NAME);
        ArgumentCaptor<Object> value = ArgumentCaptor.forClass(Object.class);
        verify(filter).setParameter(org.mockito.ArgumentMatchers.eq(TenantOwnedEntity.SOCIETY_ID_PARAM), value.capture());
        assertThat(value.getValue()).isEqualTo(societyId);
    }

    @Test
    @DisplayName("also sets the DB-layer GUC on the transaction's connection via doWork (Req 1.5, 1.10)")
    void setsDatabaseGucOnTransactionConnection() throws Exception {
        UUID societyId = UUID.randomUUID();
        when(tenantContext.currentSocietyId()).thenReturn(societyId);
        when(session.enableFilter(TenantOwnedEntity.FILTER_NAME)).thenReturn(filter);
        // Capture the Work unit handed to doWork and run it against a stub connection, so we can
        // assert the activator delegates the GUC set to the TenantScope on that exact connection.
        java.sql.Connection connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            org.hibernate.jdbc.Work work = invocation.getArgument(0);
            work.execute(connection);
            return null;
        }).when(session).doWork(org.mockito.ArgumentMatchers.any());

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);
        activator.activate(session);

        // The app-layer filter and the DB-layer GUC are both set, from the one resolved context,
        // on the one transaction's connection.
        verify(filter).setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, societyId);
        verify(tenantScope).applyToSession(connection);
    }

    @Test
    @DisplayName("aborts and does not enable the filter when the context is unresolved (Req 1.6)")
    void abortsWhenUnresolved() {
        when(tenantContext.currentSocietyId()).thenThrow(new TenantContextUnresolvedException());

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);

        assertThatThrownBy(() -> activator.activate(session))
                .isInstanceOf(TenantContextUnresolvedException.class);
        // Crucially, the filter is NEVER enabled on an unresolved request, so no unscoped query can
        // run (the resolve-first ordering guarantees this). Neither is the DB GUC touched.
        verify(session, never()).enableFilter(org.mockito.ArgumentMatchers.anyString());
        verify(session, never()).doWork(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("the only society id bound to the filter comes from TenantContext (Req 1.5, BR1)")
    void bindsOnlyServerDerivedSociety() {
        UUID serverSociety = UUID.randomUUID();
        when(tenantContext.currentSocietyId()).thenReturn(serverSociety);
        when(session.enableFilter(TenantOwnedEntity.FILTER_NAME)).thenReturn(filter);

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);
        activator.activate(session);

        // The activator exposes no API that accepts a caller-supplied society; the value it binds is
        // exactly what the (server-derived) TenantContext returned.
        verify(filter).setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, serverSociety);
    }

    @Test
    @DisplayName("no-arg activate() unwraps the session from the current EntityManager")
    void noArgActivateUsesEntityManagerSession() {
        UUID societyId = UUID.randomUUID();
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(tenantContext.currentSocietyId()).thenReturn(societyId);
        when(session.enableFilter(TenantOwnedEntity.FILTER_NAME)).thenReturn(filter);

        SocietyFilterActivator activator =
                new SocietyFilterActivator(tenantContext, entityManager, tenantScope);
        activator.activate();

        verify(entityManager).unwrap(Session.class);
        verify(filter).setParameter(TenantOwnedEntity.SOCIETY_ID_PARAM, societyId);
    }
}
