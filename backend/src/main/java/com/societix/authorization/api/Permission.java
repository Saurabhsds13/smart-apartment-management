package com.societix.authorization.api;

/**
 * The discrete, server-enforced operations of the platform ? one value per row of the authoritative
 * Permission Matrix (task 12.1; design.md "Role/permission model"; Req 3.1, 3.7, 3.8).
 *
 * <p>Each protected operation across the domain modules maps to exactly one {@code Permission}. The
 * {@link PermissionMatrix} maps each (role, permission) pair to the {@link PermissionScope} at which
 * the role may perform it (or {@link PermissionScope#NONE}). Authorization checks (tasks 12.2, 12.3)
 * consult the matrix rather than hard-coding role lists at call sites, so the matrix stays the
 * single source of truth.
 */
public enum Permission {
    /** Onboard, suspend, or reactivate a Society (platform administration). */
    SOCIETY_LIFECYCLE_MANAGE,
    /** View aggregate platform metrics. */
    PLATFORM_METRICS_VIEW,
    /** Manage a Society's configuration. */
    SOCIETY_CONFIG_MANAGE,
    /** Create/update/delete buildings and flats. */
    PROPERTY_STRUCTURE_MANAGE,
    /** Manage resident (owner/tenant) associations. */
    RESIDENT_MANAGE,
    /** Manage family members of one's own flat. */
    FAMILY_MEMBER_MANAGE,
    /** Assign roles to accounts within a Society. */
    ROLE_ASSIGN,
    /** Publish announcements. */
    ANNOUNCEMENT_PUBLISH,
    /** View announcements. */
    ANNOUNCEMENT_VIEW,
    /** Raise a maintenance request. */
    MAINTENANCE_REQUEST_RAISE,
    /** Assign or resolve a maintenance request. */
    MAINTENANCE_REQUEST_ASSIGN,
    /** Update the status of a maintenance request. */
    MAINTENANCE_REQUEST_UPDATE_STATUS,
    /** Configure charge rules. */
    CHARGE_RULE_CONFIGURE,
    /** Generate invoices. */
    INVOICE_GENERATE,
    /** Record a payment / issue a receipt. */
    PAYMENT_RECORD,
    /** Record payment adjustments, credits, or refunds. */
    PAYMENT_ADJUST,
    /** View one's own (or the society's) dues and invoices. */
    DUES_VIEW,
    /** Record a visitor entry or exit. */
    VISITOR_RECORD,
    /** Approve or deny a visit to one's own flat. */
    VISIT_DECIDE,
    /** View the visitor log. */
    VISITOR_LOG_VIEW,
    /** Configure facilities. */
    FACILITY_CONFIGURE,
    /** Create or cancel a booking. */
    BOOKING_MANAGE,
    /** Approve or reject a booking. */
    BOOKING_APPROVE,
    /** View bookings. */
    BOOKING_VIEW,
    /** Generate operational/financial reports. */
    REPORT_GENERATE,
    /** View the audit trail. */
    AUDIT_TRAIL_VIEW,
    /** Manage MFA settings. */
    MFA_MANAGE,
    /** Handle data-principal / privacy requests. */
    PRIVACY_REQUEST_HANDLE,
    /** View a vendor's scoped task list. */
    VENDOR_TASK_VIEW
}
