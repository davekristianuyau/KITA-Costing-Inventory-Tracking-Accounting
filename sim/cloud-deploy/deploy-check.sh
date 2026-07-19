#!/usr/bin/env bash
# 010 — deploy one cloud's REAL 001 module against its Floci emulator: apply → verify (state list) →
# destroy → assert no residue. No real cloud; Terraform pinned 1.9.8 (containerized). See contracts/deploy-check.md.
# Usage: bash sim/cloud-deploy/deploy-check.sh <aws|gcp|azure>
set -uo pipefail
cd "$(dirname "$0")/../.." # repo root

CLOUD="${1:?usage: deploy-check.sh <aws|gcp|azure>}"
COMPOSE="docker compose -p kita-clouddeploy -f sim/cloud-deploy/docker-compose.yml"
case "$CLOUD" in
  aws)   SVC=floci;     PORT=4566 ;;
  gcp)   SVC=floci-gcp; PORT=4588 ;;
  azure) SVC=floci-az;  PORT=4577 ;;
  *) echo "unknown cloud: $CLOUD" >&2; exit 2 ;;
esac
ROOT="sim/cloud-deploy/$CLOUD"
TF="$COMPOSE run --rm -T terraform -chdir=$ROOT"

fail() {
  echo "DEPLOY-CHECK FAIL [$CLOUD]: $*" >&2
  $TF destroy -auto-approve -input=false >/dev/null 2>&1 || true
  exit 1
}

echo "== [$CLOUD] start emulator ($SVC) =="
$COMPOSE up -d "$SVC" >/dev/null
echo -n "   waiting for :$PORT"
for _ in $(seq 1 40); do curl -s -o /dev/null "http://localhost:$PORT/" 2>/dev/null && { echo " ok"; break; }; echo -n "."; sleep 2; done

echo "== [$CLOUD] init + apply (dummy creds, endpoint→Floci) =="
$TF init -input=false >/dev/null 2>&1 || fail "init"
$TF apply -auto-approve -input=false || fail "apply"

echo "== [$CLOUD] verify: terraform state list =="
n=$($TF state list 2>/dev/null | grep -c .)
[ "${n:-0}" -gt 0 ] || fail "no resources in state after apply"
echo "   $n resources applied"

echo "== [$CLOUD] destroy =="
$TF destroy -auto-approve -input=false || fail "destroy"
left=$($TF state list 2>/dev/null | grep -c .)
[ "${left:-0}" -eq 0 ] || fail "residue: $left resource(s) remain in state"

echo "DEPLOY-CHECK PASS [$CLOUD] (applied $n, destroyed clean, dummy creds only)"
