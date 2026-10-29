package com.societix.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.societix.identity.api.SessionLifecyclePolicy;

/**
 * Unit tests for {@link SessionLifecyclePolicy} (task 10.2; Req 2.10, 2.11, 2.12).
 *
 * <p>Pure policy, so these are plain example tests with no Spring/servlet/clock dependencies: they
 * pin the role-based idle timeouts, the absolute lifetime cap, and the expiry decision at and
 * around each boundary.
 */
@DisplayName("SessionLifecyclePolicy")
class SessionLifecyclePolicyTest {

    @Test
    @DisplayName("privileged roles get the 30-minute idle timeout (Req 2.10)")
    void privilegedIdleTimeout() {
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("SOCIETY_ADMIN")))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("ACCOUNTANT")))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("FACILITY_MANAGER")))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("PLATFORM_ADMIN")))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("resident-only roles get the 60-minute idle timeout (Req 2.11)")
    void residentIdleTimeout() {
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("OWNER")))
                .isEqualTo(Duration.ofMinutes(60));
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("TENANT_RESIDENT", "FAMILY_MEMBER")))
                .isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("a mixed privileged+resident role set uses the stricter privileged timeout (Req 2.10)")
    void mixedRolesUseStricterTimeout() {
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("OWNER", "SOCIETY_ADMIN")))
                .as("holding any privileged role must not grant the lenient resident window")
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("an empty or unknown role set defaults to the stricter privileged timeout")
    void emptyOrUnknownRolesUseStricterTimeout() {
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of())).isEqualTo(Duration.ofMinutes(30));
        assertThat(SessionLifecyclePolicy.idleTimeoutFor(Set.of("VENDOR"))).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("absolute lifetime cap is 12 hours (Req 2.12)")
    void absoluteLifetime() {
        assertThat(SessionLifecyclePolicy.absoluteLifetime()).isEqualTo(Duration.ofHours(12));
    }

    @Test
    @DisplayName("a session idle beyond its role timeout is expired (Req 2.10, 2.11)")
    void expiresWhenIdleExceedsTimeout() {
        // Resident idle for 61 minutes, well under the 12h age cap -> expired by idle.
        assertThat(SessionLifecyclePolicy.isExpired(
                Duration.ofMinutes(61), Duration.ofHours(1), Set.of("OWNER")))
                .isTrue();
        // Privileged idle for 31 minutes -> expired by idle.
        assertThat(SessionLifecyclePolicy.isExpired(
                Duration.ofMinutes(31), Duration.ofHours(1), Set.of("ACCOUNTANT")))
                .isTrue();
    }

    @Test
    @DisplayName("a session within idle and age limits is not expired")
    void notExpiredWithinLimits() {
        assertThat(SessionLifecyclePolicy.isExpired(
                Duration.ofMinutes(59), Duration.ofHours(11), Set.of("OWNER")))
                .isFalse();
        assertThat(SessionLifecyclePolicy.isExpired(
                Duration.ofMinutes(29), Duration.ofHours(11), Set.of("SOCIETY_ADMIN")))
                .isFalse();
    }

    @Test
    @DisplayName("a session older than 12 hours is expired regardless of activity (Req 2.12)")
    void expiresWhenAgeExceedsAbsoluteCap() {
        // Freshly active (0 idle) but 12h01m old -> expired by absolute cap, even for a resident.
        assertThat(SessionLifecyclePolicy.isExpired(
                Duration.ofSeconds(1), Duration.ofHours(12).plusMinutes(1), Set.of("OWNER")))
                .isTrue();
    }
}
