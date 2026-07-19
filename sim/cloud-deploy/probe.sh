#!/usr/bin/env bash
# 010 — measure a cloud's Floci coverage (FR-011, contracts/probe.md): apply the real 001 module (emulated
# mode) against the emulator, list the resource types that applied (supported), and the types the module
# guards behind `var.emulated` (skipped — unsupported or too-slow). Writes sim/cloud-deploy/coverage/<cloud>.md.
# Usage: bash sim/cloud-deploy/probe.sh <aws|gcp|azure>
#
# NOTE: full "unsupported" discovery for a new cloud is done by attempting `emulated=false` apply and adding a
# `count = var.emulated ? 0 : 1` guard for each resource the emulator errors on (as done for aws_db_instance).
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root

CLOUD="${1:?usage: probe.sh <aws|gcp|azure>}"
COMPOSE="docker compose -p kita-clouddeploy -f sim/cloud-deploy/docker-compose.yml"
case "$CLOUD" in
  aws)   SVC=floci;     PORT=4566 ;;
  gcp)   SVC=floci-gcp; PORT=4588 ;;
  azure) SVC=floci-az;  PORT=4577 ;;
  *) echo "unknown cloud: $CLOUD" >&2; exit 2 ;;
esac
ROOT="sim/cloud-deploy/$CLOUD"
MODULE="infra/terraform/modules/$CLOUD"
TF="$COMPOSE run --rm -T terraform -chdir=$ROOT"
OUT="sim/cloud-deploy/coverage/$CLOUD.md"
mkdir -p sim/cloud-deploy/coverage

echo "== [$CLOUD] start emulator + apply (emulated) =="
$COMPOSE up -d "$SVC" >/dev/null
for _ in $(seq 1 40); do curl -s -o /dev/null "http://localhost:$PORT/" 2>/dev/null && break; sleep 2; done
$TF init -input=false >/dev/null 2>&1
$TF apply -auto-approve -input=false >/dev/null 2>&1 || { echo "apply failed — inspect with deploy-check.sh"; }

supported=$($TF state list 2>/dev/null | sed -E 's/^module\.[a-z]+\.//; s/\[.*$//; s/\..*$//' | sort -u)
skipped=$(grep -rlE 'count *= *var\.emulated' "$MODULE" 2>/dev/null | xargs -r grep -oE 'resource "[a-z_]+"' | sed 's/resource //' | tr -d '"' | sort -u)

{
  echo "# Floci $CLOUD coverage — 001 modules/$CLOUD (auto-probed $(date +%F))"
  echo
  echo "## Supported (applied against Floci, emulated mode)"
  echo '```'; echo "$supported"; echo '```'
  echo "## Skipped when emulated (guarded \`count = var.emulated ? 0 : 1\` — unsupported or too slow)"
  echo '```'; echo "${skipped:-<none>}"; echo '```'
} > "$OUT"

$TF destroy -auto-approve -input=false >/dev/null 2>&1
echo "coverage written → $OUT"
