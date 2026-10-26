#!/bin/sh
# Local-dev role bootstrap for the Societix PostgreSQL container.
#
# Runs ONCE, the first time the postgres data directory is initialized (standard
# docker-entrypoint-initdb.d behavior). Creates the NON-SUPERUSER, NOBYPASSRLS application
# runtime role the app connects as, so the Row-Level Security backstop (task 8.2) is
# actually enforced — see infra/README.md "Database roles and Row-Level Security".
#
# This mirrors, for local dev, the provisioning a DBA performs out of band in real
# environments. The POSTGRES_USER superuser owns the schema here; in CI/deploy a separate
# migration/owner role owns objects and runs Flyway.
#
# SOCIETIX_DB_USERNAME / SOCIETIX_DB_PASSWORD are injected by docker-compose from .env.
set -eu

: "${SOCIETIX_DB_USERNAME:?SOCIETIX_DB_USERNAME must be set}"
: "${SOCIETIX_DB_PASSWORD:?SOCIETIX_DB_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     --set=app_user="$SOCIETIX_DB_USERNAME" \
     --set=app_pass="$SOCIETIX_DB_PASSWORD" \
     --set=db_name="$POSTGRES_DB" <<'EOSQL'
-- Create the runtime role if it does not already exist. NOSUPERUSER + NOBYPASSRLS are
-- CREATE ROLE defaults, stated explicitly as the security contract (infra/README.md):
-- the app must never connect as a superuser or a BYPASSRLS role.
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS', :'app_user', :'app_pass')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

-- Let the runtime role connect and use the schema plus the tables/sequences Flyway will
-- create. Only DML is granted; the runtime role owns no tenant-owned tables.
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
EOSQL
