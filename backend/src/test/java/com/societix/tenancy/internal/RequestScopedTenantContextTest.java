package com.societix.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.societix.tenancy.api.TenantContextUnresolvedException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Focused unit tests for {@link RequestScopedTenantContext} (Req 1.2, 1.3, 1.6, BR1).
 *
 * <p>These drive the holder directly — no Spring context — and pin down the three resolution
 * states the tenant-scoping layer relies on:
 * <ul>
 *   <li><strong>unresolved</strong>: {@code isResolved()==false}, not platform-admin, and
 *       {@code currentSocietyId()} aborts (Req 1.6);</li>
 *   <li><strong>society</strong>: resolved to exactly one owning society derived server-side;</li>
 *   <li><strong>platform-admin</strong>: resolved, platform-scoped, no single society.</li>
 * </ul>
 * They also confirm the holder refuses a second resolution so a wiring bug cannot silently
 * overwrite an established tenant scope.
 */
@DisplayName("RequestScopedTenantContext")
class RequestScopedTenantContextTest {

    @Nested
    @DisplayName("when freshly created (unresolved / anonymous request)")
    class Unresolved {

        private final RequestScopedTenantContext context = new RequestScopedTenantContext();

        @Test
        @DisplayName("is not resolved and is not platform-admin")
        void reportsUnresolved() {
            assertThat(context.isResolved()).isFalse();
            assertThat(context.isPlatformAdmin()).isFalse();
        }

        @Test
        @DisplayName("aborts currentSocietyId() rather than returning a society (Req 1.6)")
        void currentSocietyIdAborts() {
            assertThatExceptionOfType(TenantContextUnresolvedException.class)
                    .isThrownBy(context::currentSocietyId);
        }
    }

    @Nested
    @DisplayName("when resolved to a society")
    class ResolvedToSociety {

        private final RequestScopedTenantContext context = new RequestScopedTenantContext();
        private final UUID societyId = UUID.randomUUID();

        @Test
        @DisplayName("reports resolved, not platform-admin, and returns the society id")
        void reportsResolvedSociety() {
            context.resolveSociety(societyId);

            assertThat(context.isResolved()).isTrue();
            assertThat(context.isPlatformAdmin()).isFalse();
            assertThat(context.currentSocietyId()).isEqualTo(societyId);
        }

        @Test
        @DisplayName("rejects a null society id")
        void rejectsNullSociety() {
            assertThatNullPointerException().isThrownBy(() -> context.resolveSociety(null));
        }
    }

    @Nested
    @DisplayName("when resolved to platform-admin")
    class ResolvedToPlatformAdmin {

        private final RequestScopedTenantContext context = new RequestScopedTenantContext();

        @Test
        @DisplayName("reports resolved and platform-admin but has no single society")
        void reportsPlatformAdmin() {
            context.resolvePlatformAdmin();

            assertThat(context.isResolved()).isTrue();
            assertThat(context.isPlatformAdmin()).isTrue();
            assertThatExceptionOfType(TenantContextUnresolvedException.class)
                    .isThrownBy(context::currentSocietyId);
        }
    }

    @Nested
    @DisplayName("resolution is single-shot per request")
    class SingleShot {

        @Test
        @DisplayName("rejects resolving a society after already resolving one")
        void rejectsSecondSocietyResolution() {
            RequestScopedTenantContext context = new RequestScopedTenantContext();
            context.resolveSociety(UUID.randomUUID());

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> context.resolveSociety(UUID.randomUUID()));
        }

        @Test
        @DisplayName("rejects resolving platform-admin after a society was resolved")
        void rejectsPlatformAdminAfterSociety() {
            RequestScopedTenantContext context = new RequestScopedTenantContext();
            context.resolveSociety(UUID.randomUUID());

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(context::resolvePlatformAdmin);
        }

        @Test
        @DisplayName("rejects resolving a society after platform-admin was resolved")
        void rejectsSocietyAfterPlatformAdmin() {
            RequestScopedTenantContext context = new RequestScopedTenantContext();
            context.resolvePlatformAdmin();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> context.resolveSociety(UUID.randomUUID()));
        }
    }
}
