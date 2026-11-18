#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Compose mounts database/schema/database.sql read-only at /opt/reloop/database.sql.
# Explicit one-time operation on this Compose project's database, never on a host DB_URL.
wait_seconds=${POSTGRES_INIT_TIMEOUT_SECONDS:-60}
case "$wait_seconds" in
  *[!0-9]*)
    echo 'POSTGRES_INIT_TIMEOUT_SECONDS deve ser um número inteiro positivo.' >&2
    exit 2
    ;;
esac
if ! [ "$wait_seconds" -gt 0 ] 2>/dev/null; then
  echo 'POSTGRES_INIT_TIMEOUT_SECONDS deve ser um número inteiro positivo.' >&2
  exit 2
fi
if ! docker compose up -d --wait --wait-timeout "$wait_seconds" postgres; then
  echo "PostgreSQL não ficou saudável dentro do limite de ${wait_seconds}s ou não pôde ser iniciado. SQL não executado." >&2
  exit 1
fi
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
