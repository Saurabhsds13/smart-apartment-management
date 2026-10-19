# Requirements Document

## Introduction

Societix is a production-grade, multi-tenant apartment and housing society management SaaS platform for the Indian market, with the tagline "Smarter Societies. Better Living." The platform digitizes the operations of residential housing societies: identity and access management, society and property structure, resident and committee communication, maintenance billing and collection, visitor and security management, facility bookings, and SaaS-level administration.

The system is built as a modular monolith with a Java Spring Boot backend, a React + TypeScript frontend, REST APIs, and a PostgreSQL database managed with Flyway migrations. This repository is greenfield: only a `.git` folder exists at the start.

This document specifies the requirements for the platform. It intentionally separates the Minimum Viable Product (MVP) scope from deferred enhancements, and sequences work by dependency across milestones M0 through M8 rather than attempting all modules simultaneously. Foundational security — multi-tenant isolation and server-side authorization — is treated as a first-class requirement that constrains every other module.

The default persistence strategy is a shared PostgreSQL schema in which every tenant-owned row carries an explicit tenant ownership column and tenant scoping is enforced in the data-access layer. This document records that as the preferred approach; any deviation must be justified by analysis during design.

### Product Vision and Measurable Goals

**Vision:** Give every housing society a single, trustworthy system to run its daily operations, collect dues accurately, keep residents informed, and keep the premises secure — without per-society custom software.

**Measurable goals (targets to be validated during design and tracked post-launch):**

- **G1 — Tenant isolation integrity:** Zero confirmed cross-tenant data exposures across all access paths (APIs, search, exports, background jobs, file URLs, caches, reports).
- **G2 — Billing correctness:** 100% of generated maintenance invoices reconcile to configured charge rules with no monetary rounding discrepancy greater than the smallest INR unit (1 paisa) per line item.
- **G3 — Collection visibility:** Society Admins and Accountants can view the dues and collection status of every unit in their society within the application without manual spreadsheets.
- **G4 — Security log completeness:** 100% of financial, security, and administrative state changes produce an immutable audit record attributing the actor, action, timestamp, and tenant.
- **G5 — Accessibility:** User-facing web screens meet WCAG 2.2 Level AA success criteria as verified by automated checks plus documented manual review.
- **G6 — Availability and performance:** The platform meets the performance and availability targets defined in the Non-Functional Requirements section under expected production load.

Specific numeric targets for latency, availability, and load are defined in the Non-Functional Requirements and recorded as approved engineering objectives in the Resolved Decisions section (see RD1); representative workloads, monitoring, and exclusions are to be refined during design.

## Glossary

- **Platform**: The top-level Societix SaaS system that hosts and serves all societies.
- **Tenant**: An isolated data and operational boundary. In this platform a Tenant corresponds one-to-one with a Society.
- **Society**: A single housing society or apartment complex that subscribes to the Platform. The unit of multi-tenancy.
- **Building**: A named structure within a Society. Also referred to as a Wing or Block.
- **Wing**: A subdivision or named section of a Building. Treated as a Building-level grouping for addressing purposes.
- **Flat**: A single residential unit within a Building/Wing. Also referred to as a Unit or Apartment.
- **Unit**: Synonym for Flat; the smallest addressable residential space that can be owned or occupied.
- **Resident**: A person associated with a Flat, either as an Owner, Tenant, or Family Member.
- **Platform_Admin**: An operator of the Platform (Societix staff) who manages societies, subscriptions, and platform-wide configuration. Not scoped to a single Society.
- **Society_Admin**: An administrator or elected committee member who manages a single Society. Also covers Committee Member responsibilities.
- **Committee_Member**: An elected or appointed office bearer of a Society with administrative privileges, modeled as a role within Society_Admin scope.
- **Facility_Manager**: A property or facility manager responsible for day-to-day operations of one or more Societies' premises and facilities.
- **Accountant**: A finance operator who manages charges, invoices, receipts, and financial reports for a Society. Also referred to as Finance_Operator.
- **Security_Guard**: A gate or premises security operator who records visitor entries and exits for a Society.
- **Owner**: A Resident who legally owns a Flat.
- **Tenant_Resident**: A Resident who rents and occupies a Flat under an Owner. (Named distinctly from "Tenant" to avoid confusion with the multi-tenancy term.)
- **Family_Member**: A Resident associated with a Flat under an Owner or Tenant_Resident, without independent account-management privileges by default.
- **Maintenance_Staff**: A worker who performs maintenance tasks and updates maintenance request status for a Society.
- **Vendor**: An external service provider engaged by a Society, with limited, scoped access.
- **Auditor**: A read-only user who can view data within an assigned scope but cannot modify any data.
- **User_Account**: A set of credentials and identity attributes that can authenticate to the Platform.
- **Role**: A named set of permissions assigned to a User_Account within a scope.
- **Permission**: A discrete authorization to perform a specific operation on a specific resource type.
- **Tenant_Context**: The authenticated and authorized Society scope derived server-side for a request, used to scope all data access.
- **Authentication_Service**: The platform component that verifies User_Account credentials and issues sessions or tokens.
- **Authorization_Service**: The platform component that evaluates whether an authenticated actor may perform a requested operation within a Tenant_Context.
- **Audit_Service**: The platform component that records immutable audit records for sensitive operations.
- **Billing_Service**: The platform component that generates maintenance charges and invoices for Flats.
- **Payment_Gateway**: An external, specified payment processor. No payment gateway is integrated until explicitly specified and approved.
- **Invoice**: A dated statement of charges owed by a Flat for a billing period.
- **Receipt**: A record of a payment applied against one or more Invoices.
- **Charge_Rule**: A configurable rule that determines the amount and applicability of a maintenance charge.
- **Visitor_Record**: A record of a visitor's entry to, and optional exit from, a Society's premises.
- **Facility**: A bookable or managed shared amenity within a Society (for example a clubhouse or hall).
- **Booking**: A reservation of a Facility for a time window by a Resident.
- **Notification**: A message delivered to a user through an in-application channel, and through external channels where configured and specified.
- **Audit_Record**: An immutable log entry capturing actor, action, target, timestamp, and Tenant for a sensitive operation.
- **INR**: Indian Rupee, the platform's monetary currency. The smallest unit is one paisa (1/100 of a rupee).
- **IST**: Indian Standard Time (UTC+05:30), the platform's default display time zone.
- **MVP**: Minimum Viable Product — the initial launch scope defined in this document.
- **IDOR**: Insecure Direct Object Reference, a class of access-control vulnerability.
- **BOLA**: Broken Object-Level Authorization, a class of access-control vulnerability.
- **MFA**: Multi-Factor Authentication, requiring a second authentication factor in addition to a password.
- **TOTP**: Time-based One-Time Password, an authenticator-app second factor.
- **DPDP**: The Digital Personal Data Protection Act, 2023, together with the Digital Personal Data Protection Rules, 2025, of India.
- **Data_Principal**: An individual to whom personal data relates, as used in Indian data protection law.
- **Account_Credit**: An unallocated monetary balance recorded for a Flat from an overpayment, available for authorized allocation to future Invoices or refund.

## Requirements

### Requirement 1: Multi-Tenant Isolation (Foundational Security)

**User Story:** As a Platform operator, I want strict data and operational isolation between societies, so that no society can access, infer, or affect another society's data through any path.

#### Acceptance Criteria

1. THE Platform SHALL associate every tenant-owned data record with exactly one owning Society identifier, and SHALL reject any attempt to persist a tenant-owned record whose Society identifier is null, empty, or not a registered Society with a validation error that indicates a missing or invalid Society identifier.
2. WHEN the Platform processes any request for tenant-owned data, THE Authorization_Service SHALL derive the Tenant_Context exclusively from the authenticated and authorized server-side session or token.
3. IF a request does not carry a valid authenticated session or token from which a Tenant_Context can be derived, THEN THE Authorization_Service SHALL reject the request with an authorization error and SHALL NOT access any tenant-owned data.
4. IF a request body, query parameter, path, or header supplies a Society identifier that differs from the server-derived Tenant_Context, THEN THE Authorization_Service SHALL reject the request with an authorization error and SHALL NOT use the supplied identifier to scope data access.
5. WHEN the Platform executes any query against tenant-owned data, THE Platform SHALL constrain results to the Tenant_Context of the requesting actor.
6. IF a query, search, list, export, or background job against tenant-owned data is executed without a resolved Tenant_Context, THEN THE Platform SHALL abort the operation with an error and SHALL return zero tenant-owned records.
7. IF an actor requests a tenant-owned resource by direct identifier that belongs to a different Society, THEN THE Authorization_Service SHALL return a not-found or authorization error that is indistinguishable from the response for a nonexistent resource and SHALL NOT disclose whether the resource exists.
8. WHEN the Platform performs a search or list operation over tenant-owned data, THE Platform SHALL return only records within the requesting actor's Tenant_Context.
9. WHEN the Platform generates an export, report, or file download of tenant-owned data, THE Platform SHALL include only records within the requesting actor's Tenant_Context.
10. WHEN a background job processes tenant-owned data, THE Platform SHALL scope the job's reads and writes to exactly one Society the job is executing for, and SHALL NOT read or write records belonging to any other Society within that job execution.
11. WHEN the Platform issues a URL for a stored file containing tenant-owned data, THE Platform SHALL require the requester to pass authentication and Tenant_Context authorization before the file content is served.
12. WHEN the Platform issues a URL for a stored file containing tenant-owned data, THE Platform SHALL cause the URL to expire within 900 seconds of issuance, after which THE Platform SHALL deny access to the file content with an authorization error.
13. WHEN the Platform caches tenant-owned data, THE Platform SHALL key the cache entry by Society identifier so that a cache read for one Society cannot return another Society's data.
14. WHERE an actor is a Platform_Admin, THE Authorization_Service SHALL permit cross-Society access only through platform-administration operations, and SHALL record an Audit_Record containing the acting Platform_Admin identity, the accessed Society identifier, the operation performed, and the access timestamp for each such access before the access completes.

### Requirement 2: User Accounts and Authentication

**User Story:** As a user of the platform, I want to create an account and sign in securely, so that I can access the functions permitted to my role.

#### Acceptance Criteria

1. WHEN a user submits a registered identifier and a password that matches the stored hash, THE Authentication_Service SHALL establish an authenticated session or issue an access token bound to the user's identity within 3 seconds.
2. IF a user submits credentials that do not match a registered identifier and stored hash, THEN THE Authentication_Service SHALL reject the sign-in attempt and SHALL return an error that does not reveal whether the account exists.
3. WHEN a user sets or changes a password, THE Authentication_Service SHALL require the password to be between 12 and 128 characters and SHALL reject passwords outside that range with a validation error.
4. WHEN the Platform stores a user password, THE Authentication_Service SHALL store only a salted one-way hash produced by an industry-standard adaptive password hashing algorithm.
5. WHEN a user requests a password reset, THE Authentication_Service SHALL issue a single-use reset credential that expires 60 minutes after issuance, delivered through the configured email channel.
6. IF a reset credential is expired or already used, THEN THE Authentication_Service SHALL reject the reset request and SHALL leave the current password unchanged.
7. WHEN the number of failed sign-in attempts for an account exceeds 5 within a 15-minute window, THE Authentication_Service SHALL block further sign-in attempts for that account for an initial lockout period of 15 minutes.
8. IF an account incurs repeated lockout cycles, THEN THE Authentication_Service SHALL apply progressively longer temporary lockout delays for that account and SHALL NOT impose a permanent lockout.
9. IF a sign-in is attempted for an account that is currently blocked, THEN THE Authentication_Service SHALL reject the attempt without revealing whether the credentials were valid.
10. WHILE an authenticated session belongs to a Platform_Admin, Society_Admin, Accountant, or Facility_Manager role, WHEN that session reaches 30 minutes of inactivity, THE Authentication_Service SHALL invalidate the session and require re-authentication.
11. WHILE an authenticated session belongs to an Owner, Tenant_Resident, or Family_Member role, WHEN that session reaches 60 minutes of inactivity, THE Authentication_Service SHALL invalidate the session and require re-authentication.
12. WHEN an authenticated session reaches a maximum lifetime of 12 hours from establishment, THE Authentication_Service SHALL invalidate the session and require re-authentication regardless of activity.
13. WHEN a user initiates a sensitive operation that requires elevated assurance, THE Authentication_Service SHALL require the user to reauthenticate before THE Platform performs the sensitive operation.
14. WHERE account verification is required for a new or changed account email, THE Authentication_Service SHALL require the user to confirm control of the email address through a single-use verification credential delivered through the configured email channel before THE Platform treats the email address as verified.
15. WHEN a user signs out, THE Authentication_Service SHALL invalidate the associated session or token so that it can no longer authenticate subsequent requests.

### Requirement 3: Role-Based Authorization and Permission Matrix

**User Story:** As a Society Admin, I want each user to have a defined role with specific permissions, so that users can only perform the operations their role allows.

#### Acceptance Criteria

1. THE Platform SHALL support the following roles: Platform_Admin, Society_Admin, Facility_Manager, Accountant, Security_Guard, Owner, Tenant_Resident, Family_Member, Maintenance_Staff, Vendor, and Auditor.
2. WHEN an actor requests any protected operation, THE Authorization_Service SHALL evaluate the actor's role and permissions on the server before executing the operation.
3. IF an actor lacks the Permission required for a requested operation, THEN THE Authorization_Service SHALL reject the operation with an authorization error and SHALL NOT perform any partial state change.
4. WHERE a role is Auditor, THE Authorization_Service SHALL permit read operations within the assigned Society scope and SHALL reject all operations that create, update, or delete data.
5. WHERE a role is Platform_Admin, THE Authorization_Service SHALL permit platform-administration operations and SHALL NOT grant implicit membership in any Society's resident-facing operations without an explicit, audited action.
6. WHEN a Society_Admin assigns a role to a User_Account within the Society_Admin's Society, THE Authorization_Service SHALL scope that role assignment to that Society only.
7. IF a Society_Admin attempts to assign a role to a User_Account outside the Society_Admin's Tenant_Context, THEN THE Authorization_Service SHALL reject the assignment and SHALL NOT change any role assignment.
8. THE Platform SHALL enforce the permission matrix defined in the "Permission Matrix" section as the authoritative mapping of roles to permitted operations.

### Requirement 4: Society and Property Structure Management

**User Story:** As a Society Admin, I want to define my society's buildings, wings, and flats, so that residents and operations can be organized against an accurate property structure.

#### Acceptance Criteria

1. WHEN a Platform_Admin onboards a Society, THE Platform SHALL create a Society record and associate exactly one initial Society_Admin User_Account with that Society, and IF the initial Society_Admin User_Account cannot be associated, THEN THE Platform SHALL reject the onboarding, SHALL NOT create the Society record, and SHALL return an error indicating that initial administrator association failed.
2. WHEN a Society_Admin creates a Building within the Society_Admin's Society, THE Platform SHALL record the Building under the Tenant_Context of that Society with a Building name of 1 to 100 characters.
3. WHEN a Society_Admin creates a Flat within a Building, THE Platform SHALL associate the Flat with that Building and with the owning Society, with a Flat identifier of 1 to 50 characters.
4. IF a Society_Admin attempts to create a Flat with an identifier that duplicates an existing Flat identifier within the same Building, THEN THE Platform SHALL reject the creation with a validation error indicating a duplicate Flat identifier and SHALL NOT create the Flat or modify any existing Flat.
5. WHEN a Society_Admin updates the property structure, THE Platform SHALL preserve existing associations between Flats and Residents unless the Society_Admin explicitly reassigns them.
6. THE Platform SHALL model the hierarchy as Platform containing Societies, Society containing Buildings, Building containing Flats, and Flat associated with Residents.
7. IF a Society_Admin attempts to create or modify a Building or Flat under a Society outside the Society_Admin's Tenant_Context, THEN THE Authorization_Service SHALL reject the operation with an authorization error and SHALL NOT perform any partial state change.
8. IF a Society_Admin attempts to delete a Building or Flat that has one or more active Resident associations, THEN THE Platform SHALL reject the deletion with a validation error indicating that active Resident associations exist and SHALL retain the Building or Flat and its associations.

### Requirement 5: Resident Management

**User Story:** As a Society Admin, I want to manage owners, tenants, and family members for each flat, so that the society has an accurate resident directory.

#### Acceptance Criteria

1. WHEN a Society_Admin assigns an Owner to a Flat, THE Platform SHALL record the Owner association within the owning Society.
2. WHEN a Society_Admin assigns a Tenant_Resident to a Flat, THE Platform SHALL record the Tenant_Resident association and SHALL retain the existing Owner association for that Flat.
3. WHEN an Owner or Tenant_Resident adds a Family_Member to their Flat, THE Platform SHALL associate the Family_Member with that Flat under the owning Society.
4. IF an actor attempts to associate a Resident with a Flat outside the actor's Tenant_Context, THEN THE Authorization_Service SHALL reject the operation with an authorization error and SHALL NOT perform any partial state change.
5. WHEN a Society_Admin ends a Tenant_Resident's occupancy of a Flat, THE Platform SHALL mark the association as ended with the end time and SHALL retain the historical record.
6. IF a Society_Admin attempts to assign a Tenant_Resident to a Flat that already has an active Tenant_Resident association, THEN THE Platform SHALL reject the assignment with a validation error indicating an existing active occupancy and SHALL retain the current active Tenant_Resident association.
7. IF an actor attempts to add a Family_Member to a Flat with which that actor has no active Owner or Tenant_Resident association, THEN THE Authorization_Service SHALL reject the operation with an authorization error and SHALL NOT create the Family_Member association.

### Requirement 6: Communication and Announcements

**User Story:** As a Society Admin, I want to publish announcements to residents, so that residents receive official society communications in one place.

#### Acceptance Criteria

1. WHEN a Society_Admin publishes an announcement with a non-empty title of at most 200 characters and a body of at most 5,000 characters, THE Platform SHALL make the announcement visible to the targeted Residents within the owning Society within 5 seconds of publication.
2. IF a Society_Admin submits an announcement whose title is empty, whose title exceeds 200 characters, or whose body exceeds 5,000 characters, THEN THE Platform SHALL reject the publication with a validation error indicating the offending field and SHALL NOT create the announcement or any Notification.
3. WHEN an announcement is published, THE Platform SHALL create exactly one in-application Notification for each targeted Resident.
4. WHERE an announcement does not target a specific Building or Flat, THE Platform SHALL treat the owning Society as the target scope and make the announcement visible to all Residents within that Society.
5. IF an announcement targets a specific Building or Flat, THEN THE Platform SHALL restrict visibility to Residents associated with that Building or Flat.
6. IF an announcement targets a Building or Flat that does not exist within the Society_Admin's Tenant_Context, THEN THE Platform SHALL reject the publication with a validation error and SHALL NOT create the announcement or any Notification.
7. WHEN a Resident views the announcement list, THE Platform SHALL display only announcements within the Resident's Tenant_Context and target scope.

### Requirement 7: Maintenance Requests

**User Story:** As a Resident, I want to raise maintenance requests for my flat or common areas, so that issues are tracked to resolution.

#### Acceptance Criteria

1. WHEN a Resident submits a maintenance request identifying either the Resident's associated Flat or a common area, with a non-empty description of at most 2,000 characters, THE Platform SHALL record the request with an open status and the submission time under the owning Society.
2. IF a Resident submits a maintenance request with an empty description, with a description exceeding 2,000 characters, or referencing a Flat the Resident is not associated with, THEN THE Platform SHALL reject the submission with a validation error indicating the offending field and SHALL NOT create the request.
3. WHEN a Facility_Manager or Society_Admin assigns a maintenance request to Maintenance_Staff within the same Tenant_Context, THE Platform SHALL record the assignment and SHALL update the request status to assigned.
4. IF a Facility_Manager or Society_Admin attempts to assign a maintenance request to a Maintenance_Staff account outside the request's Tenant_Context, THEN THE Authorization_Service SHALL reject the assignment and SHALL NOT change the request status.
5. WHEN assigned Maintenance_Staff updates a maintenance request to a permitted next status, THE Platform SHALL persist the new status and the time of the update.
6. WHEN a maintenance request is marked resolved, THE Platform SHALL record the resolution time and SHALL create an in-application Notification for the requesting Resident.
7. IF a Resident attempts to view a maintenance request outside the Resident's Tenant_Context, THEN THE Authorization_Service SHALL reject the request.

### Requirement 8: Maintenance Charges and Invoicing

**User Story:** As an Accountant, I want to configure charge rules and generate invoices for flats, so that maintenance dues are billed accurately and consistently.

#### Acceptance Criteria

1. WHEN an Accountant configures a Charge_Rule for the Society, THE Billing_Service SHALL store the Charge_Rule under the owning Society.
2. IF an Accountant configures a Charge_Rule with a non-positive amount or an amount not expressible as a whole number of paisa, THEN THE Billing_Service SHALL reject the configuration with a validation error and SHALL NOT store the Charge_Rule.
3. THE Billing_Service SHALL support Charge_Rule computation types of equal-per-unit, area-based, fixed-per-unit, recurring, and one-time, and SHALL support authorized discounts and adjustments applied to a computed charge.
4. WHEN an Accountant configures a Charge_Rule, THE Billing_Service SHALL record an effective date for the Charge_Rule and SHALL retain prior versions of the Charge_Rule rather than overwriting them.
5. WHEN an Accountant generates invoices for a billing period and one or more Flats are applicable, THE Billing_Service SHALL create exactly one Invoice per applicable Flat according to the Charge_Rules in effect for that billing period.
6. WHEN an Invoice is generated, THE Billing_Service SHALL store on the Invoice a snapshot of the applicable Charge_Rule version and the calculation inputs used to compute the Invoice.
7. WHEN a Charge_Rule is changed or a new Charge_Rule version takes effect, THE Billing_Service SHALL apply the change only to Invoices generated on or after the effective date and SHALL NOT recalculate or alter previously issued Invoices.
8. WHEN an Accountant generates invoices for a billing period for which no Flats are applicable, THE Billing_Service SHALL create zero Invoices and SHALL report that no applicable Flats were found.
9. THE Billing_Service SHALL represent and compute all monetary amounts in INR as whole numbers of paisa, SHALL NOT use binary floating-point representation, and SHALL NOT introduce rounding error greater than one paisa per line item.
10. WHEN an Invoice is generated, THE Billing_Service SHALL record the charge lines, total amount, billing period, and target Flat, and SHALL ensure each charge line and the Invoice total are greater than or equal to zero paisa.
11. IF invoice generation is requested for a billing period and Society for which Invoices already exist, THEN THE Billing_Service SHALL reject the duplicate generation, SHALL NOT create any duplicate Invoice, SHALL leave existing Invoices unchanged, and SHALL return an error indicating invoices already exist for the period.
12. WHEN an Accountant views the collection status, THE Platform SHALL display, per Flat, the invoiced amount, amount paid, and outstanding balance within the Tenant_Context.

### Requirement 9: Payment Recording and Receipts

**User Story:** As an Accountant, I want to record payments against invoices and issue receipts, so that collection is tracked without any fabricated payment flow.

#### Acceptance Criteria

1. WHEN an Accountant records a payment against one or more Invoices against the appropriate Society and Flat, THE Billing_Service SHALL create a Receipt, SHALL record the payment reference, and SHALL reduce the outstanding balance of the targeted Invoices by the applied amount.
2. IF a payment is recorded with a non-positive amount or an amount not expressible as a whole number of paisa, THEN THE Billing_Service SHALL reject the entry with a validation error and SHALL NOT create a Receipt.
3. THE Billing_Service SHALL represent payment and balance amounts in INR as whole numbers of paisa and SHALL NOT use binary floating-point representation.
4. THE Platform SHALL NOT present or simulate any online payment collection flow until a specified Payment_Gateway integration is approved and implemented, and THE Platform SHALL NOT represent a manually recorded payment as automatically verified by a bank or Payment_Gateway.
5. WHEN a recorded payment amount exceeds the total outstanding balance of the targeted Invoices, THE Billing_Service SHALL apply the amount needed to bring each targeted Invoice to a zero balance, SHALL record the excess as an unallocated account credit for the paying Flat, and SHALL NOT reduce any Invoice below zero paisa.
6. WHEN an authorized Accountant allocates an unallocated account credit to one or more Invoices, THE Billing_Service SHALL reduce the targeted Invoice balances by the allocated amount, SHALL reduce the unallocated credit balance by the same amount, and SHALL NOT reduce any Invoice below zero paisa.
7. THE Billing_Service SHALL maintain an auditable payment and allocation ledger that records each payment, each credit, and each allocation, adjustment, and refund against the paying Flat and Society.
8. WHEN an authorized Accountant records an adjustment, overpayment handling, or refund against a credit or Receipt, THE Billing_Service SHALL record the adjustment or refund in the payment and allocation ledger and SHALL NOT silently discard any overpayment or rewrite an issued Invoice.
9. WHEN a payment record is submitted with a duplicate external reference for the same Society, THE Billing_Service SHALL reject the duplicate, SHALL NOT create a second Receipt for the same reference, and SHALL leave the existing Receipt and Invoice balances unchanged.
10. WHEN a Receipt, credit allocation, adjustment, or refund is created, THE Audit_Service SHALL record an Audit_Record capturing the actor, amount, targeted Invoices or credit, timestamp, and Society.

### Requirement 10: Visitor and Security Management

**User Story:** As a Security Guard, I want to record visitor entries and exits, so that the society has an accurate log of who entered the premises.

#### Acceptance Criteria

1. WHEN a Security_Guard records a visitor entry with a visitor name of 1 to 100 characters and an optional contact of up to 20 characters, THE Platform SHALL create a Visitor_Record with status inside and a server-recorded entry time under the owning Society.
2. IF a Security_Guard records a visitor entry with a missing or out-of-range visitor name or contact, THEN THE Platform SHALL reject the entry with a validation error and SHALL NOT create a Visitor_Record.
3. WHERE a visitor is associated with a specific Flat, THE Platform SHALL link the Visitor_Record to that Flat within the Tenant_Context.
4. WHEN a Security_Guard records a visitor exit against a Visitor_Record that is inside and within the same Tenant_Context, THE Platform SHALL update that Visitor_Record with a server-recorded exit time and SHALL set its status to exited.
5. IF a Security_Guard records a visitor exit with no matching inside Visitor_Record within the Tenant_Context, THEN THE Platform SHALL reject the exit with an error indicating no matching open entry and SHALL leave existing Visitor_Records unchanged.
6. WHEN a Society_Admin or Facility_Manager views the visitor log, THE Platform SHALL display only Visitor_Records within the Tenant_Context.
7. WHEN a Visitor_Record is created or updated, THE Audit_Service SHALL record an Audit_Record capturing the actor, action, timestamp, and Society.

### Requirement 11: Visitor Approval Coordination

**User Story:** As a Resident, I want to be notified when a visitor arrives for my flat, so that I can approve or deny the visit.

#### Acceptance Criteria

1. WHEN a Security_Guard records a visitor entry linked to a Flat, THE Platform SHALL create a Notification for the Residents of that Flat within 5 seconds of the entry.
2. WHILE a Visitor_Record has no recorded decision, WHEN a Resident of the targeted Flat approves or denies the visit, THE Platform SHALL record the decision and a server-recorded decision time against the Visitor_Record.
3. IF a visit decision is submitted for a Visitor_Record that already has a recorded decision, THEN THE Platform SHALL reject the new decision and SHALL retain the original decision.
4. IF a visit decision is submitted by an actor not associated with the targeted Flat, THEN THE Authorization_Service SHALL reject the decision and SHALL NOT change the Visitor_Record.

### Requirement 12: Facility Booking

**User Story:** As a Resident, I want to book shared facilities for a time window, so that I can reserve amenities without conflicts.

#### Acceptance Criteria

1. WHEN a Society_Admin or Facility_Manager configures a Facility, THE Platform SHALL store the Facility under the owning Society, with a configurable approval policy and a configurable capacity for the Facility.
2. WHERE a Facility's approval policy permits auto-confirmation and a Resident requests a Booking for that Facility with a start time before its end time, a start time not in the past, within the Facility's configured bookable window, and within the Facility's configured capacity, THE Platform SHALL create the Booking in a confirmed state under the Tenant_Context.
3. WHERE a Facility's approval policy requires staff or committee authorization, WHEN a Resident requests an otherwise valid Booking for that Facility, THE Platform SHALL create the Booking in a pending-approval state under the Tenant_Context and SHALL NOT treat the time window as confirmed until the Booking is approved.
4. WHEN a Facility_Manager or Society_Admin approves a pending-approval Booking within the same Tenant_Context and the time window has no conflicting confirmed Booking, THE Platform SHALL set the Booking to a confirmed state.
5. WHEN a Facility_Manager or Society_Admin rejects a pending-approval Booking within the same Tenant_Context, THE Platform SHALL set the Booking to a rejected state and SHALL release the time window for new Bookings.
6. IF a Resident requests a Booking whose start time is not before its end time, whose start time is in the past, whose window falls outside the Facility's configured bookable window, or that exceeds the Facility's configured capacity, THEN THE Platform SHALL reject the Booking with a validation error and SHALL NOT create the Booking.
7. IF a Resident requests a Booking that overlaps an existing confirmed Booking for the same Facility, THEN THE Platform SHALL reject the Booking with a conflict error and SHALL NOT create overlapping confirmed Bookings.
8. WHEN two or more Booking requests for the same Facility and overlapping time window are processed concurrently, THE Platform SHALL confirm or hold pending at most one of the requests for that window and SHALL reject each remaining conflicting request with a conflict error.
9. WHEN a Resident cancels a confirmed or pending-approval Booking before its start time, THE Platform SHALL set the Booking to cancelled and SHALL release the time window for new Bookings.
10. IF a Resident attempts to cancel a Booking that is not in a confirmed or pending-approval state or whose start time has passed, THEN THE Platform SHALL reject the cancellation with an error and SHALL leave the Booking unchanged.
11. WHEN a confirmed Booking's end time passes without the Resident having used the Facility and a Facility_Manager or Society_Admin records the Booking as a no-show, THE Platform SHALL set the Booking to a no-show state and SHALL retain the Booking record.

### Requirement 13: Platform Administration and SaaS Operations

**User Story:** As a Platform Admin, I want to onboard societies and manage their subscriptions and configuration, so that the SaaS platform can serve many societies.

#### Acceptance Criteria

1. WHEN a Platform_Admin creates a Society, THE Platform SHALL provision the Society's tenant boundary and initial configuration and SHALL set the Society to an active state.
2. IF provisioning a Society fails before completion, THEN THE Platform SHALL NOT leave a partially provisioned active Society and SHALL return an error indicating provisioning failed.
3. WHEN a Platform_Admin suspends a Society, THE Platform SHALL prevent that Society's users from signing in while preserving the Society's data.
4. WHEN a Platform_Admin reactivates a suspended Society, THE Platform SHALL restore sign-in access for that Society's users.
5. WHEN a Platform_Admin performs any cross-Society administrative action, THE Audit_Service SHALL record an Audit_Record identifying the actor, action, affected Society, and timestamp.
6. WHEN a Platform_Admin views platform-level metrics, THE Platform SHALL present aggregate data without exposing individual Societies' tenant-owned records outside an explicit, audited drill-down.
7. WHEN a Platform_Admin performs an audited drill-down into a Society's tenant-owned data, THE Audit_Service SHALL record an Audit_Record identifying the actor, the drill-down action, the affected Society, and the timestamp before the data is returned.

### Requirement 14: Reports

**User Story:** As a Society Admin or Accountant, I want to generate operational and financial reports, so that I can review society performance and dues.

#### Acceptance Criteria

1. WHEN a Society_Admin or Accountant generates a financial report, THE Platform SHALL include only data within the requesting actor's Tenant_Context.
2. WHEN a report is generated, THE Platform SHALL compute monetary totals in INR to one-paisa precision and SHALL NOT introduce rounding error greater than one paisa per line item relative to the underlying Invoices and Receipts.
3. WHEN an Auditor requests a report within the Auditor's assigned scope, THE Platform SHALL produce the report as a read-only output and SHALL NOT create, update, or delete any data as a result of the request.
4. IF a report request references a Society outside the actor's Tenant_Context, THEN THE Authorization_Service SHALL reject the request with an authorization error and SHALL NOT return any report data.
5. WHEN a report request matches no qualifying records within the requesting actor's Tenant_Context, THE Platform SHALL produce a report indicating zero records with monetary totals of zero rather than an error.
6. IF report generation fails before completion, THEN THE Platform SHALL return an error indicating the report could not be generated and SHALL NOT return a partial or incomplete report represented as complete.

### Requirement 15: Notifications

**User Story:** As a user, I want to receive notifications about relevant events, so that I stay informed about society activity that concerns me.

#### Acceptance Criteria

1. WHEN a platform event targets a user, THE Platform SHALL create an in-application Notification for that user within the user's Tenant_Context within 5 seconds of the event being committed.
2. WHEN a user views the Notification list, THE Platform SHALL display only Notifications addressed to that user, and SHALL indicate for each Notification whether it has been read or unread.
3. WHERE an external notification channel is configured and specified, THE Platform SHALL deliver the Notification through that channel in addition to the in-application channel.
4. THE Platform SHALL NOT claim delivery through any external channel that is not configured and implemented.
5. IF delivery through a configured external channel fails, THEN THE Platform SHALL retain the in-application Notification as delivered and SHALL record the external delivery as failed without discarding the Notification.

### Requirement 16: Audit Trail for Sensitive Operations

**User Story:** As a Society Admin or Auditor, I want an immutable audit trail of financial, security, and administrative changes, so that sensitive activity can be reviewed and attributed.

#### Acceptance Criteria

1. WHEN a financial state change is committed, THE Audit_Service SHALL record an Audit_Record capturing the actor, action, target, before-and-after summary, timestamp in IST, and Society atomically with that change, such that the state change and its Audit_Record are either both persisted or both not persisted.
2. WHEN a security state change is committed, THE Audit_Service SHALL record an Audit_Record capturing the actor, action, target, timestamp in IST, and Society atomically with that change, such that the state change and its Audit_Record are either both persisted or both not persisted.
3. WHEN an administrative state change is committed, THE Audit_Service SHALL record an Audit_Record capturing the actor, action, target, timestamp in IST, and Society atomically with that change, such that the state change and its Audit_Record are either both persisted or both not persisted.
4. THE Audit_Service SHALL store Audit_Records as append-only such that no application operation available to any application user can update or delete a stored Audit_Record.
5. WHEN an authorized reviewer queries Audit_Records, THE Platform SHALL return only Audit_Records within the reviewer's Tenant_Context, except for Platform_Admin reviewers acting under platform-administration scope.
6. IF recording an Audit_Record for a financial, security, or administrative state change cannot be completed, THEN THE Platform SHALL fail the originating operation and SHALL NOT commit the state change without its Audit_Record.

### Requirement 17: Security (Non-Functional, OWASP-Aligned)

**User Story:** As a Platform operator, I want the platform to follow established security practices, so that user data and funds-related records are protected.

#### Acceptance Criteria

1. WHEN the Platform exposes any protected operation, THE Authorization_Service SHALL enforce authorization on the server for every such operation.
2. WHEN the Platform receives input from a client, THE Platform SHALL validate and sanitize the input on the server before processing.
3. IF client input fails server-side validation, THEN THE Platform SHALL reject the request with a validation error indicating which input is invalid and SHALL NOT perform any state change for that request.
4. WHEN the Platform receives requests from a single client or account at a rate exceeding the configured per-client request-rate limit within a configured time window, THE Platform SHALL reject the excess requests with a rate-limit error and SHALL NOT process the rejected requests.
5. THE Platform SHALL store secrets and credentials outside of source control and SHALL load them from a secure configuration source at runtime.
6. WHEN a user uploads a file, THE Platform SHALL accept the file only if its type is within the configured allowed-type list and its size does not exceed the configured maximum file size, SHALL store the file outside the web-executable path, and SHALL serve the file only after authorization.
7. IF an uploaded file's type is not within the configured allowed-type list or its size exceeds the configured maximum file size, THEN THE Platform SHALL reject the upload with a validation error indicating the reason and SHALL NOT store the file.
8. WHEN the Platform transmits data between client and server, THE Platform SHALL use transport encryption.
9. IF the Platform receives a request over an unencrypted transport, THEN THE Platform SHALL reject the request and SHALL NOT process it.
10. IF the Platform detects a request that fails authorization, THEN THE Platform SHALL deny the request without exposing sensitive system details in the error response.

### Requirement 18: Privacy by Design

**User Story:** As a Resident, I want my personal data handled with privacy in mind, so that my information is collected and exposed only as needed.

#### Acceptance Criteria

1. THE Platform SHALL collect only the personal data required for the operations it performs.
2. WHEN the Platform displays personal data, THE Authorization_Service SHALL restrict visibility to actors whose role and Tenant_Context permit access to that data.
3. IF an actor requests personal data that the actor's role and Tenant_Context do not permit, THEN THE Authorization_Service SHALL deny access and SHALL NOT disclose the data.
4. THE Platform SHALL support category-specific, configurable data retention policies, and WHERE a retention policy is configured for a data category, THE Platform SHALL retain data in that category only for the configured retention period and SHALL remove or anonymize the data once that period elapses.
5. WHEN a data-principal submits an access, correction, or erasure request and an authorized actor processes it, THE Platform SHALL fulfill the request subject to records that must be preserved for financial and audit integrity and SHALL record the handling of the request.
6. WHEN an authorized actor requests deletion of a Resident's personal data under policy, THE Platform SHALL remove or anonymize the data while preserving records required for financial and audit integrity.
7. IF a deletion request is submitted by an actor not authorized to delete that Resident's personal data, THEN THE Authorization_Service SHALL reject the request and SHALL NOT remove or anonymize any data.

### Requirement 19: Accessibility (WCAG 2.2 AA)

**User Story:** As a user with a disability, I want the web interface to be accessible, so that I can use the platform with assistive technology.

#### Acceptance Criteria

1. THE Platform's user-facing web screens SHALL conform to WCAG 2.2 Level AA success criteria, verified by automated accessibility checks and documented manual review.
2. WHEN a user navigates the interface using only a keyboard, THE Platform SHALL make all interactive controls reachable and operable and SHALL NOT trap keyboard focus.
3. WHILE an interactive control has keyboard focus, THE Platform SHALL present a visible focus indicator.
4. WHEN the Platform presents form inputs, THE Platform SHALL provide programmatically associated labels and programmatically associated validation error messages.
5. WHEN the Platform presents non-text content that conveys information, THE Platform SHALL provide a text alternative that conveys equivalent information.
6. WHEN the Platform updates content dynamically in a way that conveys status, THE Platform SHALL expose that status to assistive technology.

### Requirement 20: Performance and Availability (Non-Functional)

**User Story:** As a user, I want the platform to respond promptly and remain available, so that daily society operations are not disrupted.

#### Acceptance Criteria

1. WHEN the Platform serves an ordinary read API request under expected production load, THE Platform SHALL meet the engineering objective of a 95th-percentile response time below 300 milliseconds, with representative workloads, monitoring, load testing, and exclusions defined during design.
2. WHEN the Platform serves an ordinary write API request under expected production load, THE Platform SHALL meet the engineering objective of a 95th-percentile response time below 500 milliseconds, with representative workloads, monitoring, load testing, and exclusions defined during design.
3. WHEN the Platform processes a bulk operation such as invoice generation, THE Platform SHALL complete the operation within the throughput target defined during design (default pending calibration: 1,000 invoices within 60 seconds) OR SHALL process it asynchronously and report status values of pending, in-progress, succeeded, or failed.
4. THE Platform SHALL meet the production availability engineering objective of 99.9 percent, measured over a calendar month.
5. IF a bulk or asynchronous operation fails before completion, THEN THE Platform SHALL report the operation as failed and SHALL NOT leave committed data in an inconsistent state.
6. WHEN load exceeds provisioned capacity, THE Platform SHALL degrade gracefully by shedding or queueing non-critical work rather than losing committed data.

### Requirement 21: Observability (Non-Functional)

**User Story:** As a Platform operator, I want logs, metrics, and traces, so that I can monitor health and diagnose issues.

#### Acceptance Criteria

1. WHEN the Platform handles a request, THE Platform SHALL emit a structured log entry that includes a correlation identifier, the operation, the outcome, and the Society identifier where applicable.
2. WHEN the Platform makes a downstream call while handling a request, THE Platform SHALL propagate the correlation identifier so that related log entries and traces can be linked.
3. THE Platform SHALL expose operational metrics for request rates, error rates, and latency.
4. WHEN an unhandled error occurs, THE Platform SHALL record the error with diagnostic context sufficient for diagnosis without logging secrets or full personal data records.
5. THE Platform SHALL NOT write Audit_Records to application logs in place of the Audit_Service.

### Requirement 22: Monetary Precision and Currency Handling (Non-Functional)

**User Story:** As an Accountant, I want all money handled precisely in INR, so that financial records are exact and auditable.

#### Acceptance Criteria

1. THE Platform SHALL represent all monetary amounts in INR using Java BigDecimal with a fixed precision of two decimal places (one paisa).
2. THE Platform SHALL NOT represent monetary amounts using binary floating-point types in storage or calculation.
3. WHEN the Platform applies a rounding operation to a monetary output amount, THE Platform SHALL round to two decimal places using the HALF_UP rounding policy as the documented, consistent rounding rule.
4. THE Platform SHALL document the intermediate calculation precision and the rounding boundaries applied before final two-decimal rounding.
5. WHEN the Platform computes an Invoice total, THE Platform SHALL ensure the Invoice total reconciles exactly with the sum of its line items after rounding.
6. WHEN the Platform displays a monetary amount, THE Platform SHALL format the amount in INR with two decimal places.
7. WHERE applicable tax calculations are introduced, THE Platform SHALL have the tax calculation rules reviewed by a qualified reviewer before commercial launch, and such tax calculations remain pending that review.

### Requirement 23: Time Zone Conventions (Non-Functional)

**User Story:** As a user in India, I want dates and times shown consistently, so that operations align with local time.

#### Acceptance Criteria

1. THE Platform SHALL store timestamps in UTC.
2. WHEN the Platform displays a timestamp to a user, THE Platform SHALL present it in IST by default.
3. WHERE a user configures a different display time zone, THE Platform SHALL present timestamps in the configured time zone.
4. WHEN the Platform defines a billing period or booking window, THE Platform SHALL interpret civil dates and times in IST unless otherwise configured.

### Requirement 24: Multi-Factor Authentication (MFA)

**User Story:** As a Platform operator, I want multi-factor authentication for privileged roles, so that high-impact accounts are protected beyond passwords.

#### Acceptance Criteria

1. WHERE a User_Account holds the Platform_Admin role, THE Authentication_Service SHALL require that account to complete MFA on sign-in before THE Platform grants an authenticated session.
2. WHERE a User_Account holds a privileged finance or society administration role identified by the production MFA policy, THE Authentication_Service SHALL require that account to complete MFA on sign-in before THE Platform grants an authenticated session.
3. WHERE a User_Account holds an Owner, Tenant_Resident, or Family_Member role, THE Authentication_Service SHALL offer MFA enrollment as an optional protection for that account.
4. THE Authentication_Service SHALL support a TOTP authenticator application as the initial MFA second factor.
5. WHEN a user enrolls in MFA, THE Authentication_Service SHALL complete a secure enrollment that verifies the second factor and SHALL issue single-use recovery codes to the user.
6. THE Authentication_Service SHALL store MFA secrets and recovery codes only in a protected form and SHALL NOT expose a stored MFA secret or recovery code to any application user.
7. WHEN an MFA event of enrollment, successful challenge, failed challenge, recovery-code use, or reset occurs, THE Audit_Service SHALL record an Audit_Record capturing the actor, the MFA event type, the timestamp, and the Society where applicable.
8. WHEN a user initiates a sensitive operation that requires step-up assurance, THE Authentication_Service SHALL require the user to complete reauthentication, including the MFA second factor where the account has MFA enabled, before THE Platform performs the sensitive operation.
9. IF a sign-in for an account that is required to use MFA has not completed the MFA challenge, THEN THE Authentication_Service SHALL reject the sign-in and SHALL NOT grant an authenticated session.

### Requirement 25: Indian Data Protection Compliance Readiness (DPDP)

**User Story:** As a Platform operator, I want the platform designed for Indian data protection compliance readiness, so that Societix and its societies can meet legal obligations before commercial launch.

> Note: The acceptance criteria below specify readiness and capability. Legal interpretation of the DPDP Act, 2023 and the DPDP Rules, 2025, including their commencement dates, remains pending review by qualified Indian counsel. THE Platform SHALL NOT claim legal compliance without that verification (see Open Questions OQ10).

#### Acceptance Criteria

1. WHEN the Platform collects personal data from a Data_Principal, THE Platform SHALL present a privacy notice and SHALL record the processing purposes for which the personal data is collected.
2. WHERE consent or another lawful ground is required for a processing purpose, THE Platform SHALL capture and record the consent or lawful ground before THE Platform processes the personal data for that purpose.
3. WHEN a Data_Principal submits an access, correction, or erasure request, THE Platform SHALL support fulfilling the request subject to records that must be preserved for financial, audit, and legal-hold integrity.
4. THE Platform SHALL provide a grievance-handling contact and workflow capability through which a Data_Principal can raise a concern and have it recorded.
5. THE Platform SHALL enforce category-specific retention and deletion of personal data in accordance with the configured retention policies.
6. THE Platform SHALL maintain security safeguards for personal data and SHALL provide a breach-response capability that records and supports notification of a personal-data breach.
7. THE Platform SHALL record processor and vendor relationships that involve the processing of personal data.
8. WHERE a configured policy identifies a Data_Principal as a child, THE Platform SHALL apply the configured handling for children's personal data for that Data_Principal.
9. WHEN a Society's tenancy is terminated, THE Platform SHALL support export and deletion of that Society's personal data in accordance with the configured termination policy.
10. THE Platform SHALL document the delineation of controller and processor responsibilities for personal data among Societix, each Society, and third-party providers.

## Permission Matrix

The following matrix is the authoritative mapping of roles to permitted operations (Requirement 3.7). Scope abbreviations: **P** = Platform-wide, **S** = own Society only, **F** = own Flat only, **A** = assigned scope, **—** = not permitted.

| Operation / Role | Platform_Admin | Society_Admin | Facility_Manager | Accountant | Security_Guard | Owner | Tenant_Resident | Family_Member | Maintenance_Staff | Vendor | Auditor |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Onboard/suspend Society | P | — | — | — | — | — | — | — | — | — | — |
| Platform metrics (aggregate) | P | — | — | — | — | — | — | — | — | — | — |
| Manage Society config | — | S | — | — | — | — | — | — | — | — | — |
| Manage buildings/flats | — | S | S | — | — | — | — | — | — | — | — |
| Manage residents | — | S | — | — | — | — | — | — | — | — | — |
| Manage family members | — | — | — | — | — | F | F | — | — | — | — |
| Assign roles (within Society) | — | S | — | — | — | — | — | — | — | — | — |
| Publish announcements | — | S | S | — | — | — | — | — | — | — | — |
| View announcements | — | S | S | S | S | F | F | F | — | — | A |
| Raise maintenance request | — | S | S | — | — | F | F | F | — | — | — |
| Assign/resolve maintenance request | — | S | S | — | — | — | — | — | S (assigned) | — | — |
| Update maintenance status | — | S | S | — | — | — | — | — | S (assigned) | — | — |
| Configure charge rules | — | S | — | S | — | — | — | — | — | — | — |
| Generate invoices | — | S | — | S | — | — | — | — | — | — | — |
| Record payment / issue receipt | — | S | — | S | — | — | — | — | — | — | — |
| Record payment adjustments/credits/refunds | — | S | — | S | — | — | — | — | — | — | — |
| View own dues/invoices | — | S | — | S | — | F | F | — | — | — | A |
| Record visitor entry/exit | — | S | S | — | S | — | — | — | — | — | — |
| Approve/deny visit | — | — | — | — | — | F | F | — | — | — | — |
| View visitor log | — | S | S | — | S | — | — | — | — | — | A |
| Configure facilities | — | S | S | — | — | — | — | — | — | — | — |
| Create/cancel booking | — | S | S | — | — | F | F | — | — | — | — |
| Approve/reject booking | — | S | S | — | — | — | — | — | — | — | — |
| View bookings | — | S | S | — | — | F | F | F | — | — | A |
| Generate reports | P (audited) | S | S | S | — | — | — | — | — | — | A |
| View audit trail | P (audited) | S | — | S (financial) | — | — | — | — | — | — | A |
| Manage MFA settings | P | S | — | — | — | F | F | — | — | — | — |
| Handle data-principal/privacy requests | P (audited) | S | — | — | — | — | — | — | — | — | — |
| Vendor scoped task view | — | S (grants) | S (grants) | — | — | — | — | — | — | A | — |

Notes:
- Platform_Admin access to any Society's tenant-owned data is permitted only through audited platform-administration drill-down (Requirements 1.11, 13.5, 16.5).
- Vendor access is limited to tasks explicitly granted by a Society_Admin or Facility_Manager within the granting Society.
- Family_Member permissions default to read within their Flat; management of family members is performed by the Owner or Tenant_Resident of that Flat.

## Business Rules and Edge Cases

- **BR1 — Server-derived tenancy:** Tenant_Context is always derived server-side from the authenticated session. A Society identifier supplied by the client is never used to scope data (Requirement 1.2–1.3).
- **BR2 — No duplicate invoice generation:** Invoices are generated at most once per (Society, Flat, billing period). Re-running generation for an existing period is rejected (Requirement 8.11).
- **BR3 — No duplicate payment processing:** A payment with a duplicate external reference within a Society is rejected (Requirement 9.9).
- **BR4 — Overpayment as credit:** Applying a payment cannot drive an Invoice balance below zero; any excess over the targeted outstanding balance is recorded as an unallocated Account_Credit for the paying Flat, available for authorized allocation, adjustment, or refund, and is never silently discarded (Requirement 9.5–9.8).
- **BR5 — Booking conflict prevention:** At most one confirmed Booking exists for a given Facility and overlapping time window; concurrent requests resolve to a single confirmation (Requirement 12.7–12.8).
- **BR6 — Immutable audit:** Audit_Records cannot be modified or deleted through application operations (Requirement 16.4).
- **BR7 — No fabricated integrations:** No online payment flow and no external notification channel is presented as functional unless it is configured and implemented, and a manually recorded payment is never represented as bank- or gateway-verified (Requirements 9.4, 15.4).
- **BR8 — Historical preservation:** Ending a Resident association or changing property structure preserves historical records rather than deleting them (Requirements 4.5, 5.5).
- **BR9 — Suspension preserves data:** Suspending a Society blocks sign-in but preserves all tenant-owned data (Requirement 13.3).
- **BR10 — Least-privilege reads:** Every list, search, export, and report returns only Tenant_Context-scoped data (Requirement 1.6–1.7, 14.1).
- **BR11 — Versioned charge-rule snapshot:** Charge_Rules are versioned with effective dates, and every generated Invoice preserves a snapshot of the applicable Charge_Rule version and calculation inputs (Requirement 8.3–8.6).
- **BR12 — No silent historical recalculation:** A Charge_Rule change applies only to Invoices generated on or after its effective date; previously issued Invoices are never recalculated or altered by a later rule change (Requirement 8.7).
- **BR13 — MFA for privileged roles:** MFA is enforced for Platform_Admin and for privileged finance and society administration roles under the production policy, and privileged sign-in that has not completed MFA is rejected (Requirement 24.1–24.2, 24.9).
- **BR14 — Category-specific retention:** Personal data and operational records are retained per category-specific, configurable retention policies, after which data is removed or anonymized, subject to financial, audit, and legal-hold preservation (Requirements 18.4, 25.5).
- **Edge — Concurrent invoice runs:** Two simultaneous invoice-generation requests for the same period must not both succeed; the system serializes or guards against duplication.
- **Edge — Partial payment across multiple invoices:** A single payment may be applied across multiple Invoices; the applied total must equal the recorded payment amount minus any recorded credit.
- **Edge — Visitor exit without matching entry:** Recording an exit without a matching open entry must be handled explicitly rather than creating an orphan record.
- **Edge — Resident reassignment:** Reassigning a Flat's Owner retains prior Owner history and outstanding financial obligations are not silently transferred.
- **Edge — Role change mid-session:** When a user's role changes, authorization is re-evaluated on the next protected operation rather than relying on a cached decision.

## MVP Scope vs Future Enhancements

### MVP (initial launch)

- Requirement 1 — Multi-tenant isolation (foundational)
- Requirement 2 — Authentication
- Requirement 3 — Role-based authorization and permission matrix
- Requirement 4 — Society and property structure
- Requirement 5 — Resident management
- Requirement 6 — Communication and announcements
- Requirement 7 — Maintenance requests
- Requirement 8 — Maintenance charges and invoicing
- Requirement 9 — Payment recording and receipts (manual recording; no gateway)
- Requirement 10 / 11 — Visitor and security management, including visitor approval coordination
- Requirement 12 — Facility booking
- Requirement 13 — Platform administration and SaaS operations
- Requirement 14 — Reports (core financial and operational)
- Requirement 15 — Notifications (in-application channel)
- Requirement 16 — Audit trail
- Requirements 17–23 — Non-functional baselines (security, privacy, accessibility, performance, observability, monetary precision, time zone)
- Requirement 24 — Multi-factor authentication for privileged roles
- Requirement 25 — Indian data protection compliance readiness (DPDP) — MVP readiness capability; qualified Indian legal counsel review of DPDP interpretation is a launch blocker and must be completed before commercial launch

### Deferred Enhancements (post-MVP, dependency-driven)

- Polls and voting
- Meetings and agenda management
- Events management
- Document repository
- Dedicated mobile application APIs
- WhatsApp and SMS external notification channels
- Online payment gateway integration (Payment_Gateway)
- AI-assisted support
- Advanced analytics and dashboards

Deferred enhancements are intentionally out of MVP scope and are introduced only after their dependencies (identity, tenancy, core modules, and specified integrations) are in place.

## Milestone Mapping

- **M0 — Specifications:** This requirements document and subsequent design and tasks. No production code.
- **M1 — Engineering foundation:** Repository scaffolding, modular monolith structure, PostgreSQL + Flyway baseline, CI, secrets management, observability baseline (supports Requirements 17, 21).
- **M2 — Identity and tenant foundation:** Multi-tenant isolation, authentication, role-based authorization, permission matrix enforcement (Requirements 1, 2, 3, 17).
- **M3 — Core society management:** Society and property structure, resident management (Requirements 4, 5).
- **M4 — Essential operations:** Communication/announcements, maintenance requests, visitor and security management, in-app notifications (Requirements 6, 7, 10, 11, 15).
- **M5 — Financial and facility workflows:** Charge rules, invoicing, payment recording and receipts, facility booking, core reports (Requirements 8, 9, 12, 14, 22).
- **M6 — SaaS administration:** Platform administration, society onboarding and suspension, audited platform operations (Requirements 13, 16).
- **M7 — Production hardening:** Security hardening, rate limiting, secure file handling, accessibility conformance, performance and availability validation, privacy controls (Requirements 17, 18, 19, 20).
- **M8 — Launch readiness:** End-to-end validation across all MVP requirements, operational runbooks, and go-live checks.

## Assumptions

- A1: Multi-tenancy is implemented with a shared PostgreSQL schema where every tenant-owned row carries an explicit owning-Society column, with trusted server-side tenant resolution and centralized tenant scoping in the data-access layer. PostgreSQL Row-Level Security (RLS) will be evaluated as defense-in-depth during design, documenting transaction context, connection-pooling implications, and migration strategy. Tenant isolation must be tested across APIs, queries, direct object references, background jobs, exports, reports, and file access.
- A2: One Tenant corresponds to exactly one Society in the MVP. A user does not access multiple societies unless explicitly specified and authorized. The identity and membership model is defined clearly so that each account's society membership and role are unambiguous.
- A3: The MVP uses a single primary deployment region appropriate for the Indian market. Hosting assumptions, backups, recovery objectives, disaster recovery, and external service dependencies will be documented during design. Multi-region deployment is out of MVP scope.
- A4: Email is the only initially supported external communication channel for account-related messages. The MVP provides configurable email delivery, password-reset flows, email verification where required, delivery-failure handling, and secure configuration. SMS and WhatsApp are deferred.
- A5: No payment gateway is integrated in the MVP. Authorized Accountants manually record payments against the appropriate society, flat, and invoice, with payment references, receipts, allocations, duplicate prevention, audit trails, adjustments, overpayments, and refund recording. A manually entered payment is never represented as automatically verified by a bank or payment gateway.
- A6: The frontend is a responsive React + TypeScript web application supporting desktop, tablet, and mobile browsers; native Android and iOS applications are out of scope.
- A7: The technical design will compare session-based and token-based authentication. For a same-origin web application, the initial default to evaluate is Spring Security sessions with secure HTTP-only cookies and CSRF protection. The design will document authentication, logout, session expiry, revocation, password recovery, MFA for privileged roles, and reauthentication for sensitive actions. One approach will be selected and documented before implementation.

## Open Questions

The questions below have been reviewed and resolved with the approved provisional defaults recorded in the Resolved Decisions subsection. Items requiring further legal, business, or operational review are explicitly marked as pending. OQ10 (legal interpretation) and applicable tax calculations remain pending qualified review.

- OQ1: What are the concrete numeric targets for ordinary read/write API latency and monthly availability (Requirement 20)? — RESOLVED (see RD1).
- OQ2: What is the exact overpayment policy — reject excess, or record a carryable credit (Requirement 9)? — RESOLVED (see RD2).
- OQ3: What are the failed-sign-in lockout threshold and window, and the session inactivity and lifetime limits (Requirement 2)? — RESOLVED (see RD3).
- OQ4: What data retention periods apply to personal data and operational records (Requirement 18)? — RESOLVED, provisional operational defaults (see RD4); statutory/legal-hold retention PENDING legal review.
- OQ5: Which Charge_Rule computation types does the MVP support (Requirement 8)? — RESOLVED (see RD5).
- OQ6: What is the authoritative rounding rule for monetary operations (Requirement 22)? — RESOLVED (see RD6); applicable tax calculations PENDING review before commercial launch.
- OQ7: Does MVP require a facility booking approval step, or is resident self-service booking sufficient (Requirement 12)? — RESOLVED (see RD7).
- OQ8: What identity attributes are mandatory for each resident role under privacy-by-design minimization (Requirement 18)? — RESOLVED (see RD8).
- OQ9: Which operations require multi-factor authentication in the MVP (Requirements 2, 24)? — RESOLVED (see RD9).
- OQ10: What Indian data protection or other legal/regulatory requirements constrain retention, deletion, consent, and processing (Requirement 25)? — PENDING review by qualified Indian counsel (see RD10).

## Resolved Decisions

The following records the approved provisional defaults for the open questions above. Decisions marked PENDING require qualified legal, business, or operational review before commercial launch and are not to be treated as final or as a claim of compliance.

- **RD1 — Performance and availability (APPROVED engineering objectives, not guarantees):** Ordinary read APIs p95 below 300 ms; ordinary write APIs p95 below 500 ms; production monthly availability target 99.9%. Representative workloads, monitoring, load testing, and exclusions to be defined in design (Requirement 20).
- **RD2 — Overpayments (APPROVED):** Permit overpayments and record the excess as an unallocated account credit; maintain an auditable payment and allocation ledger; prevent duplicate processing; define authorized credit allocation, adjustment, and refund workflows; never silently discard overpayments or rewrite issued invoices (Requirement 9).
- **RD3 — Login and session security (APPROVED configurable starting defaults):** 5 failed login attempts within 15 minutes; initial temporary lockout 15 minutes with progressive delays for repeated abuse; idle timeout 30 minutes for admin/finance roles; idle timeout 60 minutes for ordinary resident roles; maximum session lifetime 12 hours; reauthentication for sensitive operations; no permanent lockouts; secure account recovery (Requirement 2).
- **RD4 — Data retention (APPROVED provisional operational defaults, NOT statutory):** audit/security logs 12 months; visitor records 90 days; notifications 12 months; temporary uploads 30 days; backups 35-day rolling retention; category-specific configurable retention. Separate policies for financial records, legal holds, disputes, and applicable regulatory requirements to be specified — PENDING legal review (Requirement 18).
- **RD5 — Charge computation (APPROVED):** Configurable, versioned charge rules supporting equal-per-unit, area-based, fixed-per-unit, recurring, and one-time charges, plus authorized discounts and adjustments, with effective dates. Invoices preserve a snapshot of the applicable rule and calculation inputs. Java BigDecimal is used for monetary calculations. Historical invoices are not silently recalculated after rule changes (Requirement 8).
- **RD6 — Rounding (APPROVED):** INR with two decimal places for monetary outputs; BigDecimal, not floating-point; HALF_UP as the provisional two-decimal rounding policy; intermediate precision and rounding boundaries documented; invoice totals reconcile with line items. Applicable tax calculations to be reviewed before commercial launch — PENDING (Requirement 22).
- **RD7 — Amenity booking approval (APPROVED):** Configurable approval policy per amenity; auto-confirm eligible bookings where policy allows; pending-approval state for facilities requiring staff or committee authorization; define conflicts, capacity, cancellations, no-shows, and refunds; prevent conflicting bookings under concurrent requests (Requirement 12).
- **RD8 — Resident identity fields (APPROVED minimization):** Require only full name; a verified email or verified mobile number per the selected auth method; account status; society membership and role; and applicable unit association with owner/tenant relationship. Do NOT require Aadhaar, PAN, date of birth, or identity-document uploads by default. Additional fields are configurable, permission-controlled, and documented with purpose and retention (Requirement 18).
- **RD9 — MFA (APPROVED):** Enforce MFA for platform super administrators; require and enforce MFA for privileged finance and society administration roles under the approved production policy; offer resident MFA; evaluate TOTP authenticator apps as the initial method; define secure recovery, secret protection, audit logging, and reauthentication (Requirements 2, 24).
- **RD10 — Indian privacy/legal (PENDING qualified counsel review):** A compliance-readiness specification is required covering the Digital Personal Data Protection Act, 2023 and the Digital Personal Data Protection Rules, 2025 and their commencement dates, privacy notices, processing purposes, consent and lawful grounds, data-principal requests, grievance handling, retention, deletion, security safeguards, breach response, vendors, children's data, and tenant termination, delineating responsibilities of Societix, each society, and third-party providers. Legal interpretations are marked for review by qualified Indian counsel. The Platform does not claim compliance without verification (Requirement 25).
