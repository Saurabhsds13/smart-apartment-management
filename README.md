# Societix — Smart Apartment Management Platform

Societix is a multi-tenant SaaS platform for managing residential societies (apartment
complexes) in the Indian market. It covers society/property structure, residents,
communication, maintenance, billing and manual payments, visitor management, facility
booking, and tenant-scoped reporting — with multi-tenant isolation and server-side
authorization built in from the ground up.

## Architecture

Societix is a **modular monolith**:

- **Backend** — Spring Boot 3.x on Java 21, organized package-by-module
  (`com.societix.<module>`). Each module exposes a public `api` package and keeps
  entities/repositories in an `internal` package; ArchUnit tests enforce the boundaries.
  Persistence is PostgreSQL with Flyway-managed migrations.
- **Frontend** — Vite + React + TypeScript (strict), served same-origin with the backend
  so it uses the server-side session cookie with CSRF protection.

One tenant corresponds to exactly one Society (MVP). Tenant scope is always resolved
server-side from the authenticated session — the client never supplies a society id to
scope data.

## Repository Layout

```
smart-apartment-management/
├── backend/            # Maven project (Spring Boot 3.x, Java 21) — modular monolith
├── frontend/           # Vite + React + TypeScript (strict) SPA
├── infra/              # reverse proxy, deployment, RLS notes
├── docs/               # ADRs, runbooks, API notes
├── scripts/            # dev/CI helper scripts
├── docker-compose.yml  # postgres, app, frontend, mailhog (local dev stack)
├── .env.example        # documents every environment variable (copy to .env)
├── .gitignore
└── README.md           # this file
```

The authoritative spec (requirements, design, implementation plan) lives under
`.kiro/specs/societix-platform/`.

## Prerequisites

Install these before working on the project:

- **JDK 21** — the backend targets Java 21.
- **Maven** — backend build and dependency management (a Maven wrapper is added with the
  backend project in a later task).
- **Docker** (with Docker Compose) — runs PostgreSQL, the app, the frontend, and MailHog
  locally, and is required for Testcontainers-based integration tests.
- **Node.js** (LTS) — frontend build and tooling via npm.

## Local Development Workflow

1. **Clone and configure.** Copy `.env.example` to `.env` and fill in local values
   (database, SMTP, file storage, session/cookie, MFA encryption key). `.env` is
   git-ignored; it must contain `SOCIETIX_MFA_ENCRYPTION_KEY` (no default) or the app
   will refuse to start.
2. **Start the stack.** Run `docker compose up` to bring up PostgreSQL, the backend
   (`app`), the frontend (Vite dev server), and MailHog. On first start the database
   provisions the non-superuser runtime role the app connects as (see `infra/README.md`).
   The app is on http://localhost:8080, the SPA on http://localhost:5173, and the MailHog
   inbox on http://localhost:8025.
3. **Backend.** The `app` container runs the Spring Boot app with the `local` profile;
   Flyway applies database migrations on startup. To run it outside Docker, start it from
   `backend/` against a running `postgres`.
4. **Frontend.** The `frontend` container runs the Vite dev server with hot reload and
   proxies `/api` to the backend same-origin. To run it outside Docker, install deps and
   `npm run dev` from `frontend/`.
5. **Test.** Run backend unit, integration (Testcontainers), and property-based tests,
   and frontend unit (Vitest), end-to-end (Playwright), and accessibility (axe) checks.

## Deployment (MVP)

The MVP uses a single primary deployment region appropriate for the Indian market
(Requirement A3). Multi-region deployment is out of scope. Hosting, backups, recovery
objectives, and disaster-recovery details are documented under `infra/` and `docs/`.
