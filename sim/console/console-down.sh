#!/usr/bin/env bash
# 011 US4 — tear the console down independently: the 009 stack first, then the imitated AWS resources,
# then floci-aws + the Floci UI (and its network). Never fails on already-gone pieces.
# Usage: sim/console/console-down.sh [client-a]
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root

CLIENT="${1:-client-a}"
ENV="${ENV:-stg}"
FLOCI="docker compose -p kita-console-floci -f sim/console/docker-compose.floci.yml"

echo "== tearing down the 009 backend/edge/frontend =="
CLIENTS="$CLIENT" bash sim/sim-down.sh || true

echo "== destroying imitated AWS resources on floci-aws (best-effort) =="
$FLOCI run --rm -T terraform destroy -auto-approve -input=false \
  -var "client_name=$CLIENT" -var "env=$ENV" || true

echo "== tearing down floci-aws + Floci UI =="
$FLOCI down -v --remove-orphans || true

echo "Console is down."
