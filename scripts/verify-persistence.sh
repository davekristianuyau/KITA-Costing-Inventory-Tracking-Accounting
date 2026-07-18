#!/usr/bin/env bash
# T017 [US1]: prove the containerized database persists committed data across a restart (SC-002, FR-004).
# Brings up the data tier + operations-service (health-gated), writes a sentinel row into the operations
# schema, restarts Postgres, and asserts the row survived. Run from the repo root with a populated `.env`.
set -euo pipefail

psql() { docker compose exec -T postgres psql -U "${POSTGRES_USER:-kita}" -d "${POSTGRES_DB:-kita}" "$@"; }
wait_healthy() { # $1 = compose service name
  for _ in $(seq 1 24); do
    [ "$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q "$1")" 2>/dev/null)" = "healthy" ] && return 0
    sleep 5
  done
  echo "FAIL: $1 did not become healthy" >&2; return 1
}

echo "==> Bringing up postgres, redis, operations-service (health-gated)"
docker compose up -d --build postgres redis operations-service
wait_healthy postgres
wait_healthy operations-service

echo "==> Writing sentinel row into the operations schema"
psql -c "create table if not exists operations._persist_check(id int); truncate operations._persist_check; insert into operations._persist_check values (42);"

echo "==> Restarting postgres (data volume must retain the row)"
docker compose restart postgres
wait_healthy postgres

echo "==> Verifying the row persisted"
got="$(psql -tA -c 'select id from operations._persist_check;')"
psql -c 'drop table operations._persist_check;' >/dev/null
if [ "$got" = "42" ]; then
  echo "PASS: committed data persisted across the postgres restart (0 data loss)."
else
  echo "FAIL: sentinel not found after restart (got '$got')" >&2; exit 1
fi
