package com.societix.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link AuditEntry} value object (task 11.1; Req 16.1-16.3, 16.5).
 *
 * <p>These pin the public contract other modules build on: the always-required attribution fields
 * ({@code action}, {@code targetType}) are validated, the optional tenant/actor fields expose their
 * presence as {@link Optional}, and the builder round-trips every field.
 */
@DisplayName("AuditEntry")
class AuditEntryTest {

    @Nested
    @DisplayName("required-field validation")
    class RequiredFields {

        @Test
        @DisplayName("rejects a blank action")
        void rejectsBlankAction() {
            assertThatThrownBy(() -> AuditEntry.builder()
                    .action("  ")
                    .targetType("Invoice")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action");
        }

        @Test
        @DisplayName("rejects a blank target type")
        void rejectsBlankTargetType() {
            assertThatThrownBy(() -> AuditEntry.builder()
                    .action("PAYMENT_RECORDED")
                    .targetType("")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("targetType");
        }
    }

    @Nested
    @DisplayName("optional tenant/actor scope")
    class OptionalScope {

        @Test
        @DisplayName("a platform-level event may omit the society id (Req 13.5/16.5)")
        void platformLevelEventHasNoSociety() {
            AuditEntry entry = AuditEntry.builder()
                    .action("SOCIETY_SUSPENDED")
                    .targetType("Society")
                    .actorUserId(UUID.randomUUID())
                    .build();

            assertThat(entry.societyId()).isNull();
            assertThat(entry.societyIdOptional()).isEmpty();
            assertThat(entry.actorUserIdOptional()).isPresent();
        }

        @Test
        @DisplayName("a system event may omit the actor id")
        void systemEventHasNoActor() {
            AuditEntry entry = AuditEntry.builder()
                    .action("LOGIN_FAILED")
                    .targetType("User")
                    .societyId(UUID.randomUUID())
                    .build();

            assertThat(entry.actorUserId()).isNull();
            assertThat(entry.actorUserIdOptional()).isEmpty();
            assertThat(entry.societyIdOptional()).isPresent();
        }
    }

    @Test
    @DisplayName("builder round-trips all fields")
    void builderRoundTrip() {
        UUID society = UUID.randomUUID();
        UUID actor = UUID.randomUUID();

        AuditEntry entry = AuditEntry.builder()
                .societyId(society)
                .actorUserId(actor)
                .action("ROLE_ASSIGNED")
                .targetType("UserSocietyMembership")
                .targetId("membership-42")
                .beforeSummary("{\"role\":null}")
                .afterSummary("{\"role\":\"Accountant\"}")
                .build();

        assertThat(entry.societyId()).isEqualTo(society);
        assertThat(entry.actorUserId()).isEqualTo(actor);
        assertThat(entry.action()).isEqualTo("ROLE_ASSIGNED");
        assertThat(entry.targetType()).isEqualTo("UserSocietyMembership");
        assertThat(entry.targetId()).isEqualTo("membership-42");
        assertThat(entry.beforeSummary()).isEqualTo("{\"role\":null}");
        assertThat(entry.afterSummary()).isEqualTo("{\"role\":\"Accountant\"}");
    }
}
