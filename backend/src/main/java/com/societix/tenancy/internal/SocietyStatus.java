package com.societix.tenancy.internal;

/**
 * Lifecycle state of a {@link Society} (design.md "Data Models" &gt; societies; Req 13.1, 13.3,
 * BR9).
 *
 * <p>The set is deliberately closed and mirrored by the {@code ck_societies_status} CHECK
 * constraint in {@code V2__societies.sql}, so neither the application nor raw SQL can persist an
 * out-of-range value. Suspension is a status flip only — a suspended society retains all of its
 * data; nothing is deleted (BR9). The full provisioning/suspension <em>logic</em> lands in tasks
 * 32.1/32.2; this type only models the field.
 *
 * <p>Persisted by name via {@link jakarta.persistence.EnumType#STRING} so the stored values match
 * the lowercase tokens in the database CHECK constraint ({@code 'active'} / {@code 'suspended'}).
 * The enum constants are therefore declared in lowercase to line up exactly with those tokens.
 */
public enum SocietyStatus {

    /** The society is operational. New societies are provisioned in this state (DB default). */
    active,

    /** The society is suspended; access is curtailed but all data is retained (BR9). */
    suspended
}
