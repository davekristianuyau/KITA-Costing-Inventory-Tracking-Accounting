#!/usr/bin/env bash
# T025 [US2]: executable smoke test of the containerized backend stack. Builds and starts the full
# stack, waits for every service to report healthy, verifies per-service schema isolation, and asserts
# the datastores are NOT reachable from the host. Run from the repo root; CI supplies the passwords.
# NOTE: the gateway route check (external request → service → DB) is added when the gateway lands.
set -euo pipefail
cd "$(dirname "$0")/.."

export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ci-postgres}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-ci-postgres}"

SERVICES="operations-service hr-service crm-service procurement-service workflow-service gateway"
PSQL() { docker compose exec -T postgres psql -U "${POSTGRES_USER:-kita}" -d "${POSTGRES_DB:-kita}" "$@"; }

cleanup() { docker compose down -v >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> Starting from a clean slate (fresh Postgres volume — password is set only on first init)"
docker compose down -v >/dev/null 2>&1 || true

echo "==> Building and starting the full stack"
docker compose up -d --build

echo "==> Waiting for all containers to report healthy"
for svc in postgres redis $SERVICES; do
  h=none
  for _ in $(seq 1 40); do
    h=$(docker inspect --format '{{.State.Health.Status}}' "$(docker compose ps -q "$svc")" 2>/dev/null || echo none)
    [ "$h" = healthy ] && break
    sleep 6
  done
  [ "$h" = healthy ] || { echo "FAIL: $svc did not become healthy"; docker compose logs "$svc" | tail -60; exit 1; }
  echo "  ok: $svc healthy"
done

echo "==> Verifying per-service schema isolation"
for s in operations hr crm procurement workflow; do
  n=$(PSQL -tA -c "select count(*) from information_schema.tables where table_schema='$s';")
  [ "${n:-0}" -gt 0 ] || { echo "FAIL: schema '$s' has no tables"; exit 1; }
  echo "  ok: schema '$s' has $n tables"
done

echo "==> Verifying an end-to-end request through the gateway (external → gateway → service → DB)"
gw_health=$(curl -fsS http://localhost:8081/actuator/health || true)
echo "  gateway health: ${gw_health:-<none>}"
echo "$gw_health" | grep -q '"status":"UP"' || { echo "FAIL: gateway not UP on host :8081"; exit 1; }
routed=$(curl -fsS http://localhost:8081/api/operations/items || true)
# a fresh stack has no items yet — an empty JSON array is a correct DB-backed response through the gateway
[ "$routed" = "[]" ] || { echo "FAIL: routed GET /api/operations/items returned '$routed' (expected [])"; exit 1; }
echo "  ok: gateway routed /api/operations/items -> operations-service -> DB (got [])"

echo "==> Verifying datastores are NOT reachable from the host"
for ds in postgres redis; do
  bindings=$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$(docker compose ps -q "$ds")" 2>/dev/null || echo '{}')
  case "$bindings" in
    ''|'{}'|'null') echo "  ok: $ds is private" ;;
    *) echo "FAIL: $ds publishes host ports ($bindings) — should be private"; exit 1 ;;
  esac
done

echo "PASS: full backend stack healthy, schema-isolated, datastores private."
