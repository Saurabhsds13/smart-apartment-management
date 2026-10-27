package com.societix.tenancy.internal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Bridges the Spring container to {@link TenantScopedRepositoryImpl}, which Spring Data instantiates
 * directly (it is not a Spring bean and so cannot have dependencies injected). This holder is a
 * Spring bean; on initialisation it publishes the application's {@link SocietyFilterActivator}
 * through a static accessor that the repository base class calls per read.
 *
 * <h2>Why a static bridge is sound here</h2>
 * The published {@link SocietyFilterActivator} is a singleton whose own {@code TenantContext}
 * dependency is a <em>request-scoped proxy</em>. So although the activator reference is shared
 * statically, every call resolves the correct per-request society at invocation time — no tenant
 * state is cached across requests or leaked between pooled threads. The static field holds only the
 * stateless activator, never a society id.
 *
 * <p>Access is via an {@link ObjectProvider} rather than a directly injected activator so that the
 * request-scoped proxy chain is resolved lazily, at first use inside a request, rather than at
 * container start.
 */
@Component
public class TenantScopingSupport {

    private static volatile ObjectProvider<SocietyFilterActivator> activatorProvider;

    /**
     * @param activatorProvider lazy provider for the singleton {@link SocietyFilterActivator}
     *     (which itself holds the request-scoped {@code TenantContext} proxy)
     */
    public TenantScopingSupport(ObjectProvider<SocietyFilterActivator> activatorProvider) {
        TenantScopingSupport.activatorProvider = activatorProvider;
    }

    /**
     * Returns the application's {@link SocietyFilterActivator}. Called by
     * {@link TenantScopedRepositoryImpl} at the start of every tenant-owned read/delete.
     *
     * @return the filter activator
     * @throws IllegalStateException if the Spring context has not yet initialised this holder — an
     *     indication the repository ran outside a properly wired application context
     */
    static SocietyFilterActivator filterActivator() {
        ObjectProvider<SocietyFilterActivator> provider = activatorProvider;
        if (provider == null) {
            throw new IllegalStateException(
                    "TenantScopingSupport is not initialised; the Spring context must be started "
                            + "before a tenant-scoped repository is used.");
        }
        return provider.getObject();
    }
}
