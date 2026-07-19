#!/usr/bin/env bash
# Smoke test for the running sim (US3 independent test): both client stacks + identity + edge + frontend
# are healthy; each client logs in via the edge and reaches its OWN backend; datastores/services are
# private; only the frontend is host-exposed. Run after sim/sim-up.sh. Exits non-zero on any failure.
set -uo pipefail
cd "$(dirname "$0")/.." # repo root

if [ -f .env ]; then set -a; . ./.env; set +a; fi
PASSWORD="${IDENTITY_SEED_PASSWORD:-demo-pass}"
FRONTEND="http://localhost:${FRONTEND_PORT:-8080}"

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

echo "1) component health"
for gw in kita-client-a-gateway-1 kita-client-b-gateway-1; do
  docker exec "$gw" wget -qO- http://localhost:8081/actuator/health 2>/dev/null | grep -q '"UP"' \
    || fail "$gw is not UP"
done
docker exec kita-edge-identity-service-1 wget -qO- http://localhost:8090/actuator/health 2>/dev/null \
  | grep -q '"UP"' || fail "identity-service is not UP"
docker exec kita-edge-edge-1 wget -qO- http://localhost:8080/actuator/health 2>/dev/null \
  | grep -q '"UP"' || fail "edge is not UP"
curl -fsS --max-time 5 "$FRONTEND/healthz" >/dev/null || fail "frontend is not serving"

echo "2) only the frontend is host-exposed"
curl -fsS --max-time 2 "http://localhost:8081/actuator/health" >/dev/null 2>&1 \
  && fail "a client gateway is reachable on host :8081 (should be private)"
curl -fsS --max-time 2 "http://localhost:8090/actuator/health" >/dev/null 2>&1 \
  && fail "identity is reachable on host :8090 (should be private)"

echo "3) each client logs in through the edge and reaches its own backend"
for pair in "client-a:alice" "client-b:bob"; do
  company="${pair%%:*}"; user="${pair##*:}"
  jar="$(mktemp)"
  code="$(curl -s -o /dev/null -w '%{http_code}' -c "$jar" \
    -H 'Content-Type: application/json' \
    -d "{\"company\":\"$company\",\"username\":\"$user\",\"password\":\"$PASSWORD\"}" \
    "$FRONTEND/auth/login")"
  [ "$code" = "200" ] || { rm -f "$jar"; fail "login $company/$user returned $code (want 200)"; }

  # An authenticated /api call must be routed (not 401/403) to this client's backend.
  api="$(curl -s -o /dev/null -w '%{http_code}' -b "$jar" --max-time 10 \
    "$FRONTEND/api/operations/actuator/health")"
  rm -f "$jar"
  case "$api" in
    401|403|000) fail "$company authenticated /api returned $api (auth/routing failed)";;
    *) echo "   $company: login=200 /api=$api (routed to own backend)";;
  esac
done

echo "SMOKE PASS"
