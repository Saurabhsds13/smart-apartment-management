# Implementation Plan: Societix Platform

## Overview

This plan converts the Societix design into incremental, independently verifiable coding tasks for a modular-monolith Spring Boot 3.x / Java 21 backend and a Vite + React + TypeScript (strict) frontend, backed by PostgreSQL + Flyway. Work is grouped by the milestone roadmap (M1–M10). Foundational security — multi-tenant isolation and server-side authorization — is built early (M2) and every subsequent domain task carries explicit authorization, authentication, and tenant-isolation test sub-items.

Implementation language is fixed by the design: **Java 21 + Spring Boot** (backend) and **TypeScript + React** (frontend). No language-selection question is required.

The design includes a Correctness Properties section (Properties 1–18), so property-based test tasks (jqwik / fast-check) are included and each references the property and requirement clause it validates. Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation sub-tasks are never marked optional.

Tasks depending on qualified legal/tax review (DPDP interpretation, applicable tax calculations) implement the *capability* but are explicitly marked **LEGAL-REVIEW-PENDING**: the capability is not blocked, but commercial launch requires sign-off (Req 22.7, 25 note, RD10).

## Tasks

- [ ] 1. M1 — Monorepo layout and backend project bootstrap
  - [ ] 1.1 Create monorepo directory structure
    - Create `backend/`, `frontend/`, `infra/`, `docs/`, `scripts/` directories with placeholder READMEs
    - Add root `README.md` describing the layout and dev workflow
    - _Requirements: A3_

  - [ ] 1.2 Bootstrap Spring Boot 3.x + Java 21 Maven project
    - Create `backend/pom.xml` with pinned versions for Spring Web, Security, Data JPA, Validation, Mail, Actuator, Flyway, PostgreSQL driver, springdoc-openapi, Micrometer; test deps JUnit 5, Mockito, AssertJ, Testcontainers, jqwik
    - Create `com.societix` package skeleton with module packages (`tenancy`, `identity`, `authorization`, `audit`, `notification`, `filestorage`, `societyproperty`, `resident`, `communication`, `maintenance`, `billing`, `payments`, `visitor`, `facilitybooking`, `platformadmin`, `reporting`, `common`), each with `api`/`internal` sub-packages
    - Create `SocietixApplication` main class and `application.yml` with `local`/`test`/`prod` profiles; disable `hbm2ddl` outside tests
    - _Requirements: 17.5, A1_

  - [ ] 1.3 Configure PostgreSQL + Flyway baseline and initial migration
    - Wire Flyway to `backend/src/main/resources/db/migration`
    - Create baseline migration `V1__baseline.sql` (extensions e.g. `pgcrypto`/uuid, schema conventions, `TIMESTAMPTZ` defaults)
    - Configure datasource via env; runtime DB role is non-superuser, not `BYPASSRLS`
    - _Requirements: A1, A3, 1.1_

  - [ ]* 1.4 Write migration test harness with Testcontainers
    - Verify Flyway migrations apply cleanly on a fresh PostgreSQL container
    - _Requirements: A1, A3_

- [ ] 2. M1 — Cross-cutting backend foundations
  - [ ] 2.1 Implement RFC 7807 error envelope and global exception handler
    - Create `common` problem+json DTO (`type`, `title`, `status`, `detail`, `instance`, `correlationId`, `errors[]`)
    - Create `@RestControllerAdvice` mapping validation (400), auth (401), forbidden (403), not-found (404), conflict (409), rate-limit (429) without leaking stack traces or internal ids
    - _Requirements: 17.3, 17.10_

  - [ ] 2.2 Implement structured JSON logging with correlation-id filter
    - Add servlet filter that sets/propagates a correlation id in MDC per request; include `society_id` where available
    - Configure JSON log encoder; ensure no secrets/PII are logged; audit records never written as log lines
    - _Requirements: 21.1, 21.2, 21.4, 21.5_

  - [ ] 2.3 Configure Actuator health/readiness/liveness and Micrometer metrics
    - Expose `/actuator/health` with liveness/readiness groups; secure sensitive endpoints
    - Register Micrometer meters for request rate, error rate, latency
    - _Requirements: 21.3_

  - [ ] 2.4 Configure springdoc OpenAPI at /api/v1
    - Expose OpenAPI 3 docs; gate Swagger UI to non-prod or Platform_Admin
    - Establish REST conventions (base path, pagination params, status codes) in `common`
    - _Requirements: 17.1_

  - [ ]* 2.5 Write unit tests for error envelope and correlation-id propagation
    - Assert problem+json shape and that `correlationId` matches the request log id
    - _Requirements: 17.10, 21.1_

- [ ] 3. M1 — ArchUnit module-boundary test harness
  - [ ] 3.1 Implement ArchUnit boundary tests
    - Assert no cross-module access to `*.internal` packages, no module cycles, controllers/entities never cross module boundaries
    - _Requirements: A1_

- [ ] 4. M1 — Frontend bootstrap and accessible shell
  - [ ] 4.1 Bootstrap Vite + React + TypeScript (strict)
    - Create `frontend/` with `package.json`, `vite.config.ts`, `tsconfig.json` (`strict: true`, `noUncheckedIndexedAccess`)
    - Add React Router, TanStack Query, React Hook Form, Zod; configure same-origin API client with CSRF handling
    - _Requirements: A6, 19.1_

  - [ ] 4.2 Implement Tailwind design tokens and base accessible component library
    - Configure Tailwind with design tokens (navy/charcoal foundation, teal/emerald accents, neutral surfaces); light theme first
    - Build accessible primitives: buttons, inputs with programmatic labels + error association, dialogs (focus-trapped), tables (keyboard operable), toasts (ARIA live), alerts, empty/skeleton/error states; respect `prefers-reduced-motion`
    - _Requirements: 19.2, 19.3, 19.4, 19.5, 19.6_

  - [ ] 4.3 Implement application shell and providers
    - Build role-aware shell (navigation/actions render from server-returned permissions), error boundaries, query/CSRF providers, INR + IST formatting helpers
    - _Requirements: 17.1, 22.6, 23.2_

  - [ ]* 4.4 Set up Vitest/RTL + Playwright + axe harness
    - Configure Vitest + React Testing Library, Playwright E2E, and axe automated accessibility checks
    - _Requirements: 19.1_

- [ ] 5. M1 — CI pipeline (GitHub Actions)
  - [ ] 5.1 Create docker-compose and environment config
    - `docker-compose.yml` with postgres, app, frontend, mailhog; `.env.example` documenting every variable (DB, SMTP, file storage, session/cookie, MFA encryption key); `.gitignore`
    - _Requirements: 17.5, A3, A4_

  - [ ] 5.2 Create GitHub Actions CI workflow
    - Stages: format → lint → backend build+test (unit + Testcontainers + property) → frontend build+test (Vitest + Playwright smoke + axe) → dependency vulnerability scan; fail on any quality-gate violation
    - _Requirements: 17.5_

- [ ] 6. M1 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 7. M2 — Tenancy foundation and Society entity
  - [ ] 7.1 Create societies entity and migration
    - `societies` table (id, name, status active/suspended, timestamps); JPA entity + repository in `tenancy.internal`
    - _Requirements: 13.1, 13.3, 1.1_

  - [ ] 7.2 Implement TenantContext request-scoped holder
    - Request-scoped `TenantContext` populated only from the authenticated session; `currentSocietyId()`, `isPlatformAdmin()`, `isResolved()`; never from client input
    - Servlet filter populates context after authentication
    - _Requirements: 1.2, 1.3, BR1_

  - [ ] 7.3 Implement central tenant-scoping base repository and Hibernate filter
    - Enable `societyFilter` (param `societyId`) on every session for tenant-owned entities; base repository/specification enforces the `society_id` predicate so unscoped queries are impossible
    - Abort + return zero rows when `TenantContext` unresolved; reject client-supplied society id differing from server context
    - _Requirements: 1.4, 1.5, 1.6, 1.8_

  - [ ]* 7.4 Write property test for tenant-context requirement
    - **Property 2: Tenant-context requirement for tenant-owned operations**
    - **Validates: Requirements 1.3, 1.4, 1.6**

  - [ ]* 7.5 Write tenant-isolation tests for the scoping layer
    - Cross-tenant query/list/search returns only in-tenant rows; unresolved context returns zero rows; mismatched client society id rejected
    - _Requirements: 1.4, 1.5, 1.6, 1.8_

- [ ] 8. M2 — PostgreSQL Row-Level Security (defense-in-depth)
  - [ ] 8.1 Implement RLS GUC setter and connection-pool reset
    - `TenantScope.applyToSession` issues `SET LOCAL app.current_society = :id` at transaction start; pool on-return hook issues `RESET app.current_society`
    - _Requirements: 1.5, 1.10_

  - [ ] 8.2 Add RLS policies via Flyway migration
    - Migration enables `ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` and `CREATE POLICY tenant_isolation USING (society_id = current_setting('app.current_society', true)::uuid)` on tenant-owned tables; dedicated Platform_Admin audited bypass path
    - _Requirements: 1.5, 1.14_

  - [ ]* 8.3 Write integration tests for RLS enforcement
    - Verify cross-tenant reads/writes are denied at the DB layer with RLS enabled; GUC never leaks across pooled connections
    - _Requirements: 1.5, 1.6_

- [ ] 9. M2 — Identity: users, memberships, password hashing
  - [ ] 9.1 Create users and user_society_memberships entities and migrations
    - `users` (platform-level: email UQ, email_verified, password_hash, full_name, status, failed_attempts, lockout_until); `user_society_memberships` (user_id, society_id, role enum of 11 roles, scope, status, UQ user+society+role); minimization per RD8 (no Aadhaar/PAN/DOB)
    - _Requirements: 2.1, 3.1, 3.6, 18.1, A2_

  - [ ] 9.2 Implement adaptive password hashing
    - Store only salted adaptive one-way hash; enforce 12–128 char password policy
    - _Requirements: 2.3, 2.4_

  - [ ]* 9.3 Write property test for password hashing
    - **Property 11: Password hashing (salted adaptive one-way, never plaintext, unique salt)**
    - **Validates: Requirements 2.4**

- [ ] 10. M2 — Authentication, sessions, lockout, reset, verification
  - [ ] 10.1 Implement Spring Security server-side sessions with secure cookies + CSRF
    - Session cookie `Secure`, `HttpOnly`, `SameSite`; synchronizer-token CSRF for unsafe methods; reject requests over unencrypted transport at app edge
    - _Requirements: 2.1, 17.8, 17.9, A7_

  - [ ] 10.2 Implement login throttling/lockout and session lifetime policy
    - Lockout after 5 failed attempts / 15 min, initial 15-min lockout, progressive delays, never permanent; generic error not revealing account existence
    - Idle timeout 30 min (admin/finance) / 60 min (resident), 12h absolute max lifetime; logout invalidates session
    - _Requirements: 2.2, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.15_

  - [ ] 10.3 Implement password reset and email verification
    - Single-use reset credential expiring 60 min via email; reject expired/used tokens leaving password unchanged; single-use email-verification credential before treating email as verified
    - _Requirements: 2.5, 2.6, 2.14_

  - [ ]* 10.4 Write authentication and session-policy tests
    - Lockout threshold/window, progressive non-permanent lockout, idle/absolute timeouts, logout invalidation, reset/verification token single-use + expiry, account-enumeration-safe errors
    - _Requirements: 2.2, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.14, 2.15_

- [ ] 11. M2 — Audit service (transactional append-only)
  - [ ] 11.1 Create audit_records entity, migration, and AuditService
    - `audit_records` (society_id nullable for platform events, actor, action, target_type/id, before/after summary JSONB, occurred_at UTC); `AuditService.record` runs inside the originating service transaction (no async)
    - DB-enforced immutability: REVOKE UPDATE/DELETE on runtime role + BEFORE UPDATE/DELETE trigger; no app update/delete path
    - Tenant-scoped audit queries (except Platform_Admin platform scope)
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, BR6_

  - [ ]* 11.2 Write property test for audit atomicity
    - **Property 9: Audit-atomicity invariant (state change persisted iff audit record persisted)**
    - **Validates: Requirements 16.1, 16.2, 16.3, 16.6**

  - [ ]* 11.3 Write integration test for audit immutability
    - Verify application has no update/delete path and DB trigger/privilege blocks modification of stored audit records
    - _Requirements: 16.4, BR6_

- [ ] 12. M2 — Authorization: role/permission model and Permission Matrix
  - [ ] 12.1 Encode the Permission Matrix and role/permission model
    - Define 11 roles and `Permission` values (operation × resource type); load the Permission Matrix as the authoritative mapping at startup
    - Society_Admin role assignments scoped to own society; reject assignment outside Tenant_Context
    - _Requirements: 3.1, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [ ] 12.2 Implement function-level authorization
    - `@PreAuthorize`/custom `PermissionEvaluator` checks role/permission and scope (P/S/F/A) before execution; denied ops cause no partial state change; role changes re-evaluated next request
    - _Requirements: 3.2, 3.3, 17.1_

  - [ ] 12.3 Implement object-level (BOLA/IDOR) checks
    - Every by-id access verifies object `society_id` matches TenantContext and flat-scoped roles own the object's flat; cross-tenant/unowned returns not-found indistinguishable from nonexistent
    - _Requirements: 1.7, 18.2, 18.3_

  - [ ]* 12.4 Write property test for permission-matrix enforcement
    - **Property 10: Permission-matrix enforcement (authorize iff matrix permits; no partial state change on deny)**
    - **Validates: Requirements 3.2, 3.3, 3.8**

  - [ ]* 12.5 Write object-level tenant-isolation tests (BOLA/IDOR)
    - By-id access to another society's resource returns not-found; flat-scoped roles cannot access other flats' objects
    - _Requirements: 1.7, 18.3_

- [ ] 13. M2 — Multi-factor authentication (MFA)
  - [ ] 13.1 Create mfa_credentials entity and TOTP enrollment
    - `mfa_credentials` (type totp, secret_encrypted, recovery_codes_hashed single-use, enabled); encrypted secrets at rest, hashed recovery codes; enrollment verifies factor and issues single-use recovery codes; secrets never exposed
    - _Requirements: 24.4, 24.5, 24.6_

  - [ ] 13.2 Enforce MFA for privileged roles and step-up reauthentication
    - Enforce MFA on sign-in for Platform_Admin and privileged finance/society-admin roles; offer optional for residents; reject privileged sign-in without completed MFA challenge
    - Step-up reauthentication (incl. MFA factor where enabled) before sensitive operations; audit all MFA events in-transaction
    - _Requirements: 2.13, 24.1, 24.2, 24.3, 24.7, 24.8, 24.9, BR13_

  - [ ]* 13.3 Write property test for MFA enforcement
    - **Property 12: MFA enforcement for privileged roles**
    - **Validates: Requirements 24.1, 24.2, 24.9**

  - [ ]* 13.4 Write MFA unit/integration tests
    - Enrollment verification, recovery-code single-use + hashing, secret non-exposure, step-up reauth, MFA event audit records
    - _Requirements: 24.5, 24.6, 24.7, 24.8_

- [ ] 14. M2 Checkpoint — identity and tenant isolation
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. M3 — Society property structure (buildings and flats)
  - [ ] 15.1 Implement buildings and flats entities, migrations, and CRUD
    - `buildings` (society_id, name 1–100, wing optional, UQ society+name); `flats` (society_id, building_id, identifier 1–50, area, UQ building+identifier); tenant-first indexes
    - CRUD services with authorization + tenant scoping; reject operations outside Tenant_Context; delete RESTRICT when active resident associations exist
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [ ]* 15.2 Write property test for flat-identifier uniqueness
    - **Property 13: Flat-identifier uniqueness within a building**
    - **Validates: Requirements 4.4**

  - [ ]* 15.3 Write authorization and tenant-isolation tests for buildings/flats
    - Cross-tenant create/modify/delete rejected; delete-restrict on active associations; duplicate identifier rejected
    - _Requirements: 4.4, 4.7, 4.8_

  - [ ] 15.4 Build frontend building/flat management screens
    - Validated forms (RHF + Zod), searchable/paginated tables, role-aware actions
    - _Requirements: 4.2, 4.3, 19.4_

- [ ] 16. M3 — Resident management
  - [ ] 16.1 Implement resident_associations entity, migration, and lifecycle
    - `resident_associations` (society_id, flat_id, user_id, relation owner/tenant/family, start_at, end_at, parent_association_id); partial UQ for single active tenant per flat; history preserved on end
    - Owner/tenant/family assignment, end-occupancy with history retention; family-member add requires active owner/tenant association; reject outside Tenant_Context
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, BR8_

  - [ ]* 16.2 Write property test for single active tenant association
    - **Property 14: At most one active tenant association per flat**
    - **Validates: Requirements 5.6**

  - [ ]* 16.3 Write authorization and tenant-isolation tests for residents
    - Cross-tenant association rejected; family-member add without owner/tenant association rejected; ended associations retained
    - _Requirements: 5.4, 5.5, 5.7_

  - [ ] 16.4 Build role-aware resident directory and dashboards
    - Role-aware dashboards, validated resident forms, searchable/paginated resident tables
    - _Requirements: 5.1, 5.2, 5.3, 19.4_

- [ ] 17. M3 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 18. M4 — In-app notification module
  - [ ] 18.1 Implement notifications entities, migration, and NotificationService
    - `notifications` (society_id, recipient_user_id, type, payload JSONB, read, created_at) + `notification_deliveries` (channel, status); `createInApp`; in-app always delivered; external delivery recorded separately, never claimed when unconfigured
    - Notification list shows only the user's notifications with read/unread state
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, BR7_

  - [ ]* 18.2 Write unit tests for notification creation and delivery handling
    - In-app notification retained when external channel fails; no delivery claimed for unconfigured channel; list scoped to recipient
    - _Requirements: 15.2, 15.4, 15.5_

  - [ ] 18.3 Build frontend notification UI
    - Notification list/badge with read/unread, ARIA live updates
    - _Requirements: 15.2, 19.6_

- [ ] 19. M4 — Announcements
  - [ ] 19.1 Implement announcements entity, migration, and publishing with targeting
    - `announcements` (society_id, title ≤200, body ≤5000, target_type society/building/flat, target_id, published_at); validation rejects empty/oversized fields and nonexistent targets with no partial creation
    - Society/building/flat targeting; create exactly one in-app notification per targeted resident; resident list scoped to tenant + target
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [ ]* 19.2 Write property test for announcement notification fan-out
    - **Property 15: Announcement notification fan-out (one notification per targeted resident, none for others)**
    - **Validates: Requirements 6.1, 6.3**

  - [ ]* 19.3 Write authorization and tenant-isolation tests for announcements
    - Only authorized roles publish; targets outside Tenant_Context rejected; residents see only in-scope announcements
    - _Requirements: 6.5, 6.6, 6.7_

  - [ ] 19.4 Build announcement publish/list screens
    - Validated publish form with targeting, paginated announcement list
    - _Requirements: 6.1, 6.7, 19.4_

- [ ] 20. M4 — Maintenance requests
  - [ ] 20.1 Implement maintenance_requests entity, migration, and lifecycle
    - `maintenance_requests` (society_id, flat_id nullable for common area, requested_by, description ≤2000, status open/assigned/in_progress/resolved, assignee, resolved_at, version); validation and flat-association checks
    - Submission (resident's flat or common area), assignment to in-tenant Maintenance_Staff, permitted status transitions, resolution notification to requester; tenant-scoped views
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

  - [ ]* 20.2 Write authorization and tenant-isolation tests for maintenance requests
    - Submit referencing unassociated flat rejected; assign to out-of-tenant staff rejected; view outside Tenant_Context rejected
    - _Requirements: 7.2, 7.4, 7.7_

  - [ ] 20.3 Build maintenance request screens
    - Resident raise-request form, staff/admin assignment + status board, resolution flow
    - _Requirements: 7.1, 7.3, 7.5, 19.4_

- [ ] 21. M4 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 22. M5 — Charge rules (versioned, effective-dated)
  - [ ] 22.1 Implement charge_rules and charge_rule_versions entities and migrations
    - `charge_rules` (society_id, name, active); `charge_rule_versions` (computation_type: equal_per_unit/area_based/fixed_per_unit/recurring/one_time, amount NUMERIC(19,2) >0 whole-paisa, params JSONB, effective_from); prior versions retained, never overwritten
    - Configure rule with validation (non-positive/non-whole-paisa rejected); support authorized discounts/adjustments
    - _Requirements: 8.1, 8.2, 8.3, 8.4, RD5, BR11_

  - [ ]* 22.2 Write unit tests for charge-rule validation and versioning
    - Reject invalid amounts; prior versions retained on change
    - _Requirements: 8.2, 8.4_

- [ ] 23. M5 — Monetary arithmetic and invoice generation
  - [ ] 23.1 Implement BigDecimal monetary arithmetic utilities
    - DECIMAL128 (`MathContext(34, HALF_UP)`) intermediate precision; single HALF_UP round to scale 2 per line; INR formatting; no binary floating point
    - _Requirements: 22.1, 22.2, 22.3, 22.4, 22.6_

  - [ ] 23.2 Implement invoices and invoice_lines entities, migrations, and charge engine
    - `invoices` (society_id, flat_id, billing_period, total ≥0, status, rule_snapshot JSONB, version, UQ society+flat+period) and `invoice_lines` (amount ≥0, rule_version_id, calc_input JSONB)
    - Charge engine for all 5 computation types + discounts/adjustments; per-line round then sum to total; store rule-version + calc-input snapshot; audit in same transaction
    - _Requirements: 8.5, 8.6, 8.9, 8.10, 22.5_

  - [ ] 23.3 Implement idempotent invoice-run guard and no historical recalculation
    - Reject duplicate generation for existing (society, period); zero-flat run reports no applicable flats; new rule versions apply only on/after effective date, never recalculating issued invoices
    - _Requirements: 8.7, 8.8, 8.11, BR2, BR12, Edge-ConcurrentInvoiceRuns_

  - [ ]* 23.4 Write property test for monetary structure and reconciliation
    - **Property 3: Monetary structure and reconciliation (sum of lines == total at paisa precision, all ≥0)**
    - **Validates: Requirements 8.9, 8.10, 22.3, 22.5**

  - [ ]* 23.5 Write property test for no silent historical recalculation
    - **Property 4: No silent historical recalculation**
    - **Validates: Requirements 8.7**

  - [ ]* 23.6 Write property test for invoice-generation idempotency
    - **Property 5: Invoice generation idempotency / no duplicates (including concurrent runs)**
    - **Validates: Requirements 8.11**

  - [ ]* 23.7 Write concurrency test for simultaneous invoice runs
    - Two concurrent generations for the same period produce no duplicates (Testcontainers)
    - _Requirements: 8.11, Edge-ConcurrentInvoiceRuns_

- [ ] 24. M5 — Collection status and billing UI
  - [ ] 24.1 Implement per-flat collection status read model
    - `collectionStatus` returning per-flat invoiced/paid/outstanding within Tenant_Context
    - _Requirements: 8.12_

  - [ ] 24.2 Build charge-rule and invoicing screens
    - Validated charge-rule config, invoice-run trigger with status, collection-status table
    - _Requirements: 8.1, 8.12, 19.4_

- [ ] 25. M5 — Manual payments, credits, allocations, ledger
  - [ ] 25.1 Implement receipts, payments, and allocation entities and migrations
    - `receipts` (society_id, flat_id, external_reference, amount >0, UQ society+external_reference), `payments`, `receipt_allocations`; record payment reducing targeted invoice balances, never below zero; manual-only, never represented as gateway-verified
    - Reject duplicate external reference and non-positive/non-whole-paisa amounts
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.9, BR3, BR7_

  - [ ] 25.2 Implement overpayment credits and credit allocation
    - `account_credits` (balance ≥0, version) + `credit_allocations`; excess over outstanding recorded as unallocated credit; authorized allocation reduces credit and invoice balances equally, never below zero
    - _Requirements: 9.5, 9.6, Edge-PartialPaymentMultipleInvoices_

  - [ ] 25.3 Implement adjustments, refunds, and auditable payment ledger
    - `payment_ledger` append-only (payment/credit_created/allocation/adjustment/refund); adjustments/refunds recorded explicitly, overpayments never discarded, issued invoices never rewritten; each financial event writes an audit record in the same transaction
    - _Requirements: 9.7, 9.8, 9.10_

  - [ ]* 25.4 Write property test for money conservation
    - **Property 6: Money conservation across payments, credits, and allocations (no invoice below zero)**
    - **Validates: Requirements 9.5, 9.6**

  - [ ]* 25.5 Write property test for duplicate payment-reference rejection
    - **Property 7: Duplicate payment-reference rejection**
    - **Validates: Requirements 9.9**

  - [ ]* 25.6 Write financial-correctness and concurrency tests
    - Duplicate-processing guard under concurrency; payment-amount validation; ledger entries and audit records for each money movement
    - _Requirements: 9.2, 9.7, 9.9, 9.10_

  - [ ] 25.7 Build payment recording and receipt screens
    - Validated payment form with external reference, receipt view, credit allocation UI
    - _Requirements: 9.1, 19.4_

- [ ] 26. M5 Checkpoint — billing and payments
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 27. M6 — Visitor management
  - [ ] 27.1 Implement visitor_records entity, migration, and entry/exit
    - `visitor_records` (society_id, flat_id nullable, visitor_name 1–100, contact ≤20 optional, status inside/exited, entry_at/exit_at server-recorded, decision, decision_at); flat linkage within Tenant_Context
    - Record entry (status inside) with validation; record exit against inside record; exit without matching open entry rejected explicitly (no orphan); audit on create/update; tenant-scoped visitor log
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, Edge-VisitorExitWithoutEntry_

  - [ ] 27.2 Implement visitor approval coordination
    - Notify flat residents on flat-linked entry within 5s; record single decision (approve/deny) with server time; reject decision when one already recorded; reject decision by actor not associated with the flat
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [ ]* 27.3 Write authorization and tenant-isolation tests for visitors
    - Exit without matching entry rejected; visitor log scoped to tenant; decision by non-associated actor rejected; audit records written
    - _Requirements: 10.5, 10.6, 10.7, 11.4_

  - [ ] 27.4 Build visitor entry/exit and approval screens
    - Guard entry/exit forms, visitor log table, resident approve/deny UI with notifications
    - _Requirements: 10.1, 10.6, 11.2, 19.4_

- [ ] 28. M6 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 29. M7 — Facilities and facility booking
  - [ ] 29.1 Implement facilities entity, migration, and configuration
    - `facilities` (society_id, name, approval_policy auto/staff_approval, capacity, bookable_window JSONB); configure under owning society with authorization
    - _Requirements: 12.1, RD7_

  - [ ] 29.2 Implement bookings entity, migration, and lifecycle with conflict prevention
    - `bookings` (society_id, facility_id, flat_id, requested_by, start_at, end_at, status, version); GiST exclusion constraint on (facility_id, tstzrange) for confirmed/pending states + optimistic locking
    - Auto-confirm vs pending by policy; validate start<end, not-past, within window, within capacity; reject overlap conflicts; approve/reject; cancel before start releasing window; no-show
    - _Requirements: 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.9, 12.10, 12.11_

  - [ ]* 29.3 Write property test for booking non-overlap
    - **Property 8: Booking non-overlap invariant (including concurrent requests)**
    - **Validates: Requirements 12.7, 12.8**

  - [ ]* 29.4 Write concurrency and authorization tests for bookings
    - Concurrent overlapping requests confirm at most one; others get conflict; cross-tenant booking/approve rejected
    - _Requirements: 12.8, 12.4_

  - [ ] 29.5 Build facility and booking screens
    - Facility config, booking request with validation, approval queue, cancellation/no-show UI
    - _Requirements: 12.1, 12.2, 12.9, 19.4_

- [ ] 30. M7 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 31. M8 — Reports (tenant-scoped, read-only)
  - [ ] 31.1 Implement financial and operational reports
    - Tenant-scoped financial/collection and operational reports; paisa-precision monetary totals reconciling to invoices/receipts; zero-state report (zero records, zero totals) rather than error; fail cleanly without partial report
    - Auditor read-only access produces no state change; report referencing out-of-tenant society rejected
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

  - [ ] 31.2 Implement CSV export with tenant scoping
    - CSV export includes only Tenant_Context records
    - _Requirements: 1.9, 14.1_

  - [ ]* 31.3 Write tenant-isolation and read-only tests for reports
    - Reports/exports contain only in-tenant rows; out-of-tenant request rejected; Auditor report creates no data; zero-state returns zeros
    - _Requirements: 1.9, 14.1, 14.3, 14.4, 14.5_

  - [ ] 31.4 Build report screens with zero-state and CSV download
    - Report views, zero-state UI, CSV download action
    - _Requirements: 14.5, 19.4_

- [ ] 32. M8 — Platform administration
  - [ ] 32.1 Implement society onboarding with atomic provisioning
    - Create society + associate exactly one initial Society_Admin atomically; fail provisioning (no partial active society) if initial admin association fails; set active on success
    - _Requirements: 4.1, 13.1, 13.2_

  - [ ] 32.2 Implement suspension, reactivation, and audited drill-down
    - Suspend blocks sign-in preserving data; reactivate restores sign-in; cross-society admin actions and drill-downs write audit records before access; platform metrics aggregate without cross-tenant leakage
    - _Requirements: 1.14, 13.3, 13.4, 13.5, 13.6, 13.7, BR9_

  - [ ]* 32.3 Write property test for society provisioning atomicity
    - **Property 17: Society provisioning atomicity**
    - **Validates: Requirements 4.1, 13.1, 13.2**

  - [ ]* 32.4 Write tenant-isolation tests for platform metrics and drill-down
    - Aggregate metrics expose no tenant rows; drill-down writes audit record before returning data; suspended-society sign-in blocked, data preserved
    - _Requirements: 1.14, 13.5, 13.6, 13.7_

  - [ ] 32.5 Build platform-admin screens
    - Society onboarding/suspension/reactivation, aggregate metrics, audited drill-down UI
    - _Requirements: 13.1, 13.3, 13.6, 19.4_

- [ ] 33. M8 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 34. M9 — Rate limiting and secure file handling
  - [ ] 34.1 Implement rate limiting on sensitive endpoints
    - Per-client/account request-rate limit returning 429 on excess for auth and other sensitive endpoints
    - _Requirements: 17.4_

  - [ ] 34.2 Implement secure file upload/storage and brokered URLs
    - `files` entity (society_id tenant-owned, content_type, size_bytes, storage_key); store outside web-executable path; content-type allow-list + max-size validation; brokered/signed download URLs with TTL ≤900s after authz + tenant match
    - _Requirements: 1.11, 1.12, 17.6, 17.7_

  - [ ]* 34.3 Write property test for file URL expiry and authorization
    - **Property 16: File URL expiry and authorization (TTL ≤900s, tenant-authorized)**
    - **Validates: Requirements 1.11, 1.12**

  - [ ]* 34.4 Write tenant-isolation tests for file access
    - Cross-tenant file download denied; expired URL denied; out-of-policy upload rejected and not stored
    - _Requirements: 1.11, 1.12, 17.7_

- [ ] 35. M9 — Retention jobs and tenant-scoped background processing
  - [ ] 35.1 Implement category-specific retention policies and jobs
    - `retention_policies` config with approved defaults (audit/security 12mo, visitor 90d, notifications 12mo, temp uploads 30d); scheduled jobs remove/anonymize expired data subject to financial/audit/legal-hold preservation
    - Every job resolves explicit single-society scope and sets TenantContext + GUC; never touches other societies' rows; bulk/async status pending/in-progress/succeeded/failed
    - _Requirements: 1.10, 18.4, 20.3, 20.5, 25.5, BR14_

  - [ ]* 35.2 Write property test for category-specific retention
    - **Property 18: Category-specific retention (remove/anonymize past retention unless preserved)**
    - **Validates: Requirements 18.4, 25.5**

  - [ ]* 35.3 Write tenant-isolation tests for background jobs
    - Per-society job reads/writes only its own rows; unresolved-context job aborts returning zero rows
    - _Requirements: 1.6, 1.10_

- [ ] 36. M9 — Privacy / DPDP-readiness capabilities (LEGAL-REVIEW-PENDING)
  - [ ] 36.1 Implement consent and data-principal request capabilities
    - `consent_records` (purpose, lawful ground) with privacy notice + purpose recording; `data_principal_requests` (access/correction/erasure) recorded; erasure anonymizes while preserving financial/audit integrity; reject deletion by unauthorized actor
    - **LEGAL-REVIEW-PENDING: capability only; DPDP interpretation requires qualified Indian counsel sign-off before commercial launch (RD10, OQ10)**
    - _Requirements: 18.5, 18.6, 18.7, 25.1, 25.2, 25.3_

  - [ ] 36.2 Implement grievance, breach, processor, and tenant-termination capabilities
    - `grievances` workflow, `breach_records` response capability, `processor_records`, children's-data handling flag, tenant-termination export + deletion
    - **LEGAL-REVIEW-PENDING: capability only; not a compliance claim (RD10, OQ10)**
    - _Requirements: 25.4, 25.6, 25.7, 25.8, 25.9, 25.10_

  - [ ]* 36.3 Write unit tests for privacy capabilities
    - Consent recorded before processing; erasure preserves financial/audit records; unauthorized deletion rejected; grievance/breach/processor records persisted
    - _Requirements: 18.6, 18.7, 25.2, 25.3_

- [ ] 37. M9 — Accessibility, performance, observability, backup/deployment hardening
  - [ ] 37.1 Run accessibility conformance pass
    - axe automated checks across user-facing screens; document manual review notes for WCAG 2.2 AA
    - _Requirements: 19.1_

  - [ ]* 37.2 Add performance validation against p95 targets
    - Load tests validating read p95<300ms, write p95<500ms; document workloads/exclusions; graceful degradation under overload
    - _Requirements: 20.1, 20.2, 20.4, 20.6_

  - [ ] 37.3 Implement observability dashboards
    - Dashboards for request/error/latency metrics; correlation-id trace linking
    - _Requirements: 21.2, 21.3_

  - [ ] 37.4 Implement encrypted backup + restore verification and deployment docs
    - Encrypted DB backups 35-day rolling + file-store backup; documented restore procedure verified; TLS-terminating reverse proxy config; rollback/release procedure docs (compensating forward migration)
    - _Requirements: 17.8, A3_

- [ ] 38. M9 Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 39. M10 — Commercial launch readiness
  - [ ]* 39.1 Write end-to-end Playwright journeys across MVP
    - Sign-in + MFA, raise maintenance request, generate invoices, record payment, book facility, visitor entry/exit
    - _Requirements: 19.1, 20.1_

  - [ ] 39.2 Implement onboarding/data-import workflow
    - Guided society onboarding and bulk resident/flat data-import workflow with validation
    - _Requirements: 4.1, 13.1_

  - [ ] 39.3 Author admin and resident documentation
    - Create admin and resident user documentation in `docs/`
    - _Requirements: A3_

  - [ ] 39.4 Create privacy/legal review checklist, release checklist, and known-limitations register
    - Privacy/legal review checklist (**LEGAL-REVIEW-PENDING: DPDP + applicable tax calculations require qualified counsel/reviewer sign-off before launch — Req 22.7, 25 note, RD10**); release checklist; known-limitations register in `docs/`
    - _Requirements: 22.7, 25.10_

- [ ] 40. M10 Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP; core implementation tasks are never marked optional.
- Each task references specific requirement sub-clauses for traceability; every MVP requirement (1–25) is covered by at least one implementation task.
- Foundational security and tenant isolation (M2) are built before any domain module, and every domain task carries authentication, authorization, and tenant-isolation test sub-items as required by Goal G1.
- Property-based tests (Properties 1–18) use jqwik (backend) / fast-check (frontend) at ≥100 iterations, each tagged `Feature: societix-platform, Property {n}`.
- Checkpoints provide incremental validation at milestone boundaries.
- Tasks labeled **LEGAL-REVIEW-PENDING** implement capabilities now; their *launch* requires explicit qualified legal/tax sign-off (RD10, OQ10, Req 22.7) and is not a code blocker.
- Property 1 (tenant-isolation across all read paths) is validated cumulatively by the per-module tenant-isolation test sub-items (buildings/flats, residents, announcements, maintenance, reports/exports, background jobs, file access) rather than a single task.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "4.1"] },
    { "id": 1, "tasks": ["1.4", "2.1", "2.2", "2.3", "2.4", "3.1", "4.2", "5.1"] },
    { "id": 2, "tasks": ["2.5", "4.3", "4.4", "5.2", "7.1", "7.2", "11.1"] },
    { "id": 3, "tasks": ["7.3", "8.1", "9.1", "11.2", "11.3"] },
    { "id": 4, "tasks": ["7.4", "7.5", "8.2", "8.3", "9.2", "9.3", "10.1"] },
    { "id": 5, "tasks": ["10.2", "10.3", "12.1", "13.1"] },
    { "id": 6, "tasks": ["10.4", "12.2", "12.3", "13.2"] },
    { "id": 7, "tasks": ["12.4", "12.5", "13.3", "13.4", "15.1", "18.1"] },
    { "id": 8, "tasks": ["15.2", "15.3", "15.4", "16.1", "18.2", "18.3", "22.1"] },
    { "id": 9, "tasks": ["16.2", "16.3", "16.4", "19.1", "20.1", "22.2", "23.1"] },
    { "id": 10, "tasks": ["19.2", "19.3", "19.4", "20.2", "20.3", "23.2"] },
    { "id": 11, "tasks": ["23.3", "27.1", "29.1"] },
    { "id": 12, "tasks": ["23.4", "23.5", "23.6", "23.7", "24.1", "27.2", "29.2"] },
    { "id": 13, "tasks": ["24.2", "25.1", "27.3", "27.4", "29.3", "29.4"] },
    { "id": 14, "tasks": ["25.2", "29.5"] },
    { "id": 15, "tasks": ["25.3"] },
    { "id": 16, "tasks": ["25.4", "25.5", "25.6", "25.7", "31.1", "32.1"] },
    { "id": 17, "tasks": ["31.2", "31.3", "31.4", "32.2"] },
    { "id": 18, "tasks": ["32.3", "32.4", "32.5", "34.1", "34.2", "35.1"] },
    { "id": 19, "tasks": ["34.3", "34.4", "35.2", "35.3", "36.1"] },
    { "id": 20, "tasks": ["36.2", "36.3", "37.1", "37.2", "37.3", "37.4"] },
    { "id": 21, "tasks": ["39.1", "39.2", "39.3", "39.4"] }
  ]
}
```
