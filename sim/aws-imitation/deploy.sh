#!/usr/bin/env bash
# US4 — "deploy" a client's AWS production stack locally against Floci (a drop-in LocalStack replacement;
# no real cloud, SC-005). Brings up Floci, then runs the real Terraform (in a container) reusing the 001
# naming module + AWS resources. The client's feature-008 stack is the compute/DB stand-in (research D6).
# Usage: sim/aws-imitation/deploy.sh [client-a]
set -euo pipefail
cd "$(dirname "$0")/../.." # repo root

CLIENT="${1:-client-a}"
ENV="${ENV:-stg}"
COMPOSE="docker compose -p kita-floci -f sim/aws-imitation/docker-compose.floci.yml"

echo "== starting Floci =="
$COMPOSE up -d floci
echo -n "   waiting for Floci to be ready"
for _ in $(seq 1 40); do
  if curl -s -o /dev/null "http://localhost:4566/" 2>/dev/null; then echo " ok"; break; fi
  echo -n "."; sleep 2
done

echo "== terraform init + apply for $CLIENT/$ENV (in-container; pinned to Floci; NO real AWS creds) =="
$COMPOSE run --rm -T terraform init -input=false
$COMPOSE run --rm -T terraform apply -auto-approve -input=false \
  -var "client_name=$CLIENT" -var "env=$ENV"

echo
echo "Imitated AWS deployment for $CLIENT applied to Floci."
echo "Verify: sim/aws-imitation/verify.sh $CLIENT"
