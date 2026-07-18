#!/usr/bin/env bash
# T026/T029 [US3]: fail if local and production would drift. Asserts the containerized engine versions
# are pinned to the parity targets and every in-scope service uses its own schema via currentSchema.
# Run from the repo root; wired into CI as a fast gate.
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0
check() { # file  needle  label
  if grep -qF "$2" "$1"; then echo "  ok: $3"; else echo "  DRIFT: $3 (expected '$2' in $1)"; fail=1; fi
}

echo "==> Engine version pins (parity: same major/minor local <-> production)"
check docker-compose.yml "postgres:16-alpine" "postgres pinned to 16-alpine"
check docker-compose.yml "redis:7.4-alpine"   "redis pinned to 7.4-alpine"

echo "==> Per-service schema config (currentSchema=<svc>,public)"
for s in operations hr crm procurement workflow; do
  f="backend/$s-service/src/main/resources/application.yml"
  check "$f" "currentSchema=$s,public" "$s-service uses currentSchema=$s,public"
done

if [ "$fail" = 0 ]; then
  echo "PASS: no parity drift — engines pinned and schemas consistent."
else
  echo "FAIL: parity drift detected."; exit 1
fi
