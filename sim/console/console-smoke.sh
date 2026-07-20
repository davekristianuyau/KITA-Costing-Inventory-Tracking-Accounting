#!/usr/bin/env bash
# 011 US4 smoke — the console runs locally against a live floci-aws, with 0 real cloud:
#   env up → console reachable → demo login → run the reference function (routed) → Floci UI reachable
#   → only the frontend + Floci UI are host-exposed → 0 real cloud creds.
# Self-contained: brings the env up and tears it down at the end (unless KEEP_UP=1 / NO_UP=1).
# Usage: sim/console/console-smoke.sh [client-a]
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root

CLIENT="${1:-client-a}"
FRONTEND="http://localhost:${FRONTEND_PORT:-8080}"
UI="http://localhost:4500"
PASSWORD="${IDENTITY_SEED_PASSWORD:-demo-pass}"

fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

teardown() {
  if [ "${KEEP_UP:-0}" != "1" ]; then
    echo "== teardown =="
    bash sim/console/console-down.sh "$CLIENT" >/dev/null 2>&1 || true
  fi
}
trap teardown EXIT

if [ "${NO_UP:-0}" != "1" ]; then
  echo "== bringing the console up =="
  bash sim/console/console-up.sh "$CLIENT"
fi

echo "1) 0 real cloud credentials (SC-007)"
# The imitation pins Terraform to Floci with dummy test/test; a real AWS key must not be in play.
key="${AWS_ACCESS_KEY_ID:-}"
case "$key" in
  ""|test) echo "   AWS_ACCESS_KEY_ID='${key:-<unset>}' — dummy only";;
  *) fail "AWS_ACCESS_KEY_ID looks real ('$key') — the sim must use 0 real cloud creds";;
esac
grep -q 'access_key                  = "test"' sim/aws-imitation/main.tf \
  || fail "aws-imitation is not pinned to dummy Floci creds"

echo "2) console (frontend) is reachable"
curl -fsS --max-time 5 "$FRONTEND/login" >/dev/null || fail "console not serving at $FRONTEND/login"

echo "3) Floci UI is reachable (socket mounted → no 'could not reach the container runtime')"
curl -fsS --max-time 5 "$UI/" >/dev/null || fail "Floci UI not reachable at $UI"

echo "4) only the frontend + Floci UI are host-exposed (internals private)"
curl -fsS --max-time 2 "http://localhost:4566/" >/dev/null 2>&1 \
  && fail "floci :4566 is reachable on host (emulator internals must be private)"
curl -fsS --max-time 2 "http://localhost:8090/actuator/health" >/dev/null 2>&1 \
  && fail "identity :8090 is reachable on host (must be private)"
curl -fsS --max-time 2 "http://localhost:8081/actuator/health" >/dev/null 2>&1 \
  && fail "a client gateway :8081 is reachable on host (must be private)"

echo "5) demo login through the edge"
jar="$(mktemp)"
code="$(curl -s -o /dev/null -w '%{http_code}' -c "$jar" \
  -H 'Content-Type: application/json' \
  -d "{\"company\":\"$CLIENT\",\"username\":\"alice\",\"password\":\"$PASSWORD\"}" \
  "$FRONTEND/auth/login")"
[ "$code" = "200" ] || { rm -f "$jar"; fail "login $CLIENT/alice returned $code (want 200)"; }

echo "6) run the reference function: GET /api/operations/items (routed to the client's backend)"
api="$(curl -s -o /dev/null -w '%{http_code}' -b "$jar" --max-time 10 \
  "$FRONTEND/api/operations/items")"
rm -f "$jar"
case "$api" in
  401|403|000) fail "reference function returned $api (auth/routing failed)";;
  *) echo "   /api/operations/items = $api (routed)";;
esac

echo "SMOKE PASS"
