#!/usr/bin/env bash
# 010 — deploy-check all three clouds against Floci, then tear the emulators down. One command (SC-004/SC-006).
# Usage: bash sim/cloud-deploy/run-all.sh
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root
COMPOSE="docker compose -p kita-clouddeploy -f sim/cloud-deploy/docker-compose.yml"

rc=0
for c in aws gcp azure; do
  if bash sim/cloud-deploy/deploy-check.sh "$c"; then
    echo "  [$c] PASS"
  else
    echo "  [$c] FAIL"; rc=1
  fi
done

echo "== teardown all emulators =="
$COMPOSE down -v --remove-orphans >/dev/null 2>&1 || true

if [ "$rc" -eq 0 ]; then echo "RUN-ALL PASS (all clouds)"; else echo "RUN-ALL: one or more clouds failed"; fi
exit "$rc"
