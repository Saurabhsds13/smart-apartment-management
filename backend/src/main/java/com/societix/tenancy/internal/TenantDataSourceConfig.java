package com.societix.tenancy.internal;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.societix.tenancy.api.TenantScope;

/**
 * Wires the connection-pool <em>on-return</em> reset (ADR-2; Req 1.10) by wrapping the
 * auto-configured pooled {@link DataSource} in a {@link TenantResettingDataSource}, so every
 * connection returned to the pool is scrubbed of the {@code app.current_society} GUC.
 *
 * <h2>Why a {@link BeanPostProcessor}</h2>
 * Spring Boot auto-configures the Hikari {@code DataSource} bean. Rather than replace that
 * auto-configuration (and re-declare all its properties), this post-processor decorates the
 * existing bean in place: it returns a {@link TenantResettingDataSource} wrapping the original. The
 * {@link TenantScope} is fetched lazily through an {@link ObjectProvider} because the datasource is
 * created very early in context startup — earlier than ordinary singletons — and the provider defers
 * resolving the scope bean until a connection is actually borrowed.
 *
 * <p>Transaction management, Flyway, and JPA all consume the {@code DataSource} bean, so wrapping it
 * here means the reset applies uniformly to every pooled connection without any call site needing to
 * know about it.
 */
@Configuration
public class TenantDataSourceConfig {

    /**
     * Decorates the auto-configured {@link DataSource} with the tenant-GUC on-return reset.
     *
     * @param tenantScopeProvider lazy provider for the {@link TenantScope} (resolved per-borrow, not
     *     at post-processing time, to avoid early-initialisation ordering problems)
     * @return a {@link BeanPostProcessor} that wraps the pooled datasource
     */
    @Bean
    static BeanPostProcessor tenantResettingDataSourcePostProcessor(
            ObjectProvider<TenantScope> tenantScopeProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof TenantResettingDataSource)) {
                    return new TenantResettingDataSource(
                            dataSource, new LazyTenantScope(tenantScopeProvider));
                }
                return bean;
            }
        };
    }

    /**
     * Thin {@link TenantScope} that resolves the real (request-scoped-backed) scope from the context
     * only when a connection is actually returned, avoiding eager resolution during datasource
     * post-processing.
     */
    private record LazyTenantScope(ObjectProvider<TenantScope> provider) implements TenantScope {

        @Override
        public void applyToSession(java.sql.Connection connection) throws java.sql.SQLException {
            provider.getObject().applyToSession(connection);
        }

        @Override
        public void clearOnReturn(java.sql.Connection connection) throws java.sql.SQLException {
            provider.getObject().clearOnReturn(connection);
        }
    }
}
