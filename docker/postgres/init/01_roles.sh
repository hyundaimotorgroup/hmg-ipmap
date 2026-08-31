#!/bin/bash
# Creates the application database user.
# Reads credentials from environment variables set in docker/.env.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Application user: login only, no superuser privileges.
    -- CONNECT   — allows the app to connect to this database.
    -- USAGE     — allows referencing objects in the public schema.
    -- CREATE    — allows the app to create tables/sequences in the public schema
    --             (required for schema.sql and Spring Batch table initialization).
    CREATE USER app_user WITH LOGIN PASSWORD '$APP_USER_PASSWORD';
    GRANT CONNECT ON DATABASE $POSTGRES_DB TO app_user;
    GRANT USAGE, CREATE ON SCHEMA public TO app_user;
EOSQL
