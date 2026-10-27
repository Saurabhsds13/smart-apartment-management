# infra/

Infrastructure and deployment assets for the Societix platform.

This directory is a placeholder. It will hold reverse-proxy configuration, deployment
manifests, and Row-Level Security (RLS) notes.

## Deployment assumptions (MVP)

- Single primary deployment region appropriate for the Indian market (Requirement A3).
  Multi-region deployment is out of MVP scope.
- Hosting assumptions, backups, recovery objectives, disaster recovery, and external
  service dependencies are documented here during design/implementation.

## Database roles and Row-Level Security (RLS)

Tenant isolation is enforced in two independent layers (design.md ADR-2): application-side
scoping as the primary layer and PostgreSQL Row-Level Security as a database-enforced
backstop (added table-by-table in task 8.2). For RLS to actually take effect, the database
must be provisioned with **two distinct roles**:

| Role | Attributes | Used for |
|---|---|---|
| Migration/owner role | may own objects and hold DDL rights; **not** used to serve traffic | runs Flyway migrations; owns the schema/tables |
| Application runtime role | **non-superuser**, **not `BYPASSRLS`**, owns no tenant-owned tables; granted only the DML it needs | the role the app connects as to serve requests |

Why the runtime role must not be a superuser or hold `BYPASSRLS`: PostgreSQL silently skips
all RLS policies for superusers and for roles with `BYPASSRLS` (and, unless `FORCE ROW LEVEL
SECURITY` is set, for a table's owner). If the application connected as such a role, the RLS
backstop would be inert and a single missed tenant predicate in application code would become
a silent cross-tenant data leak. `FORCE ROW LEVEL SECURITY` is additionally set per tenant-owned
table (task 8.2) so even the owner is subject to policies.

Role creation requires privileges the runtime role must not hold, so it is an operational
step performed outside the application's own migrations. Example provisioning (run once by a
superuser/DBA when standing up an environment):

```sql
-- Migration/owner role (runs Flyway, owns the schema).
CREATE ROLE societix_migrator LOGIN PASSWORD '<from-secret-store>';

-- Application runtime role: non-superuser, NO BYPASSRLS (both are PostgreSQL defaults for
-- CREATE ROLE, but stated explicitly here as the security contract).
CREATE ROLE societix_app LOGIN PASSWORD '<from-secret-store>' NOSUPERUSER NOBYPASSRLS;

-- Let the app use the schema and future tables created by the migrator; grant only DML.
GRANT USAGE ON SCHEMA public TO societix_app;
ALTER DEFAULT PRIVILEGES FOR ROLE societix_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO societix_app;
```

`SOCIETIX_DB_USERNAME`/`SOCIETIX_DB_PASSWORD` (see `application.yml`) point the running app at
`societix_app`; migrations run (in CI/deploy) as `societix_migrator`.

## Audit records: append-only immutability (Req 16.4, BR6)

The `audit_records` table (migration `V3__audit_records.sql`) is append-only: once written, an
audit record can never be modified or removed. This is enforced in two independent layers, mirroring
the two-role model above:

1. **In-migration, role-independent backstop.** A `BEFORE UPDATE OR DELETE` trigger
   (`trg_audit_records_immutable`) unconditionally raises an exception, so any `UPDATE` or `DELETE`
   against a stored audit row fails — from any role and through any path (application, ad-hoc SQL,
   or a future migration). `INSERT` is not guarded, so the append-only write path keeps working.

2. **Least-privilege operational step.** The application **runtime role** is granted only
   `INSERT`/`SELECT` on `audit_records`; its `UPDATE`/`DELETE` privileges are revoked. As with role
   creation, this is performed operationally (outside the application's own migrations) when
   provisioning the runtime role, because the migration does not create or alter that role. Run once
   by the owner/DBA after migrations, against each environment:

   ```sql
   -- Default privileges from ALTER DEFAULT PRIVILEGES (above) grant the runtime role all DML on
   -- new tables; narrow audit_records back to append-only for that role.
   REVOKE UPDATE, DELETE ON audit_records FROM societix_app;
   ```

The application itself has no update/delete code path for audit records (the `AuditService` API
exposes only `record(...)` and read queries), so these database safeguards are defense-in-depth that
make immutability a database-enforced guarantee rather than a convention.
