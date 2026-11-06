#!/bin/bash
# Runs once, when the data volume is empty.
# appointme_owner runs the migrations and owns the tables.
# appointme_app runs the application: it can change rows, but not tables or triggers.
# Its table rights are granted after each migrate: db/prod/afterMigrate__application_role_grants.sql
set -euo pipefail

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
     -v owner_password="$OWNER_PASSWORD" -v app_password="$APP_PASSWORD" <<'SQL'
CREATE ROLE appointme_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE appointme_app LOGIN PASSWORD :'app_password';

ALTER DATABASE appointme OWNER TO appointme_owner;
GRANT CONNECT ON DATABASE appointme TO appointme_app;
GRANT USAGE ON SCHEMA public TO appointme_app;
SQL
