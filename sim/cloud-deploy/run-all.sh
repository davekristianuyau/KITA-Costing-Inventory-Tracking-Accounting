#!/usr/bin/env bash
# 010 — deploy-check the clouds whose Floci emulator can run the real 001 module, then tear the emulators
# down. Currently AWS only: Floci deploys the near-complete AWS module; Floci-GCP/Azure emulate only
# storage+secrets (not the compute/DB the GCP/Azure modules need), so those are deferred (see coverage/gcp.md).
# Usage: bash sim/cloud-deploy/run-all.sh
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root
COMPOSE="docker compose -p kita-clouddeploy -f sim/cloud-deploy/docker-compose.yml"

CLOUDS="${CLOUDS:-aws}"
rc=0
for c in $CLOUDS; do
  if bash sim/cloud-deploy/deploy-check.sh "$c"; then echo "  [$c] PASS"; else echo "  [$c] FAIL"; rc=1; fi
done

echo "== teardown all emulators =="
$COMPOSE down -v --remove-orphans >/dev/null 2>&1 || true

if [ "$rc" -eq 0 ]; then echo "RUN-ALL PASS ($CLOUDS)"; else echo "RUN-ALL: one or more clouds failed"; fi
exit "$rc"
