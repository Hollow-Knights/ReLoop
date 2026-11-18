#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Explicit one-time operation on this Compose project's database, never on a host DB_URL.
docker compose exec -T postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT pg_advisory_lock(hashtextextended('reloop-manual-init', 0));
SELECT NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public') AS empty_schema \gset
\if :empty_schema
  \i /opt/reloop/database.sql
\else
  DO $guard$ BEGIN
    RAISE EXCEPTION 'Inicialização recusada: public já contém tabelas. Nenhum DDL ou seed foi executado.';
  END $guard$;
\endif
SQL
