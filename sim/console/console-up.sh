#!/usr/bin/env bash
# 011 US4 — one documented startup for the whole service console, locally, with 0 real cloud:
#   1) floci-aws WITH the Docker socket (+ root) so it runs real compute, and the Floci UI (:4500)
#   2) the client's AWS resources "deployed" to floci-aws (reuses the 010 aws-imitation Terraform)
#   3) the 009 backend/edge/frontend (reuses sim/sim-up.sh)
# Only the frontend (:8080) and the Floci UI (:4500) are host-exposed.
# Usage: sim/console/console-up.sh [client-a]
set -euo pipefail
cd "$(dirname "$0")/../.." # repo root

CLIENT="${1:-client-a}"
ENV="${ENV:-stg}"
FLOCI="docker compose -p kita-console-floci -f sim/console/docker-compose.floci.yml"

echo "== 1/3 starting floci-aws (Docker socket → real compute) + Floci UI =="
$FLOCI up -d floci floci-ui

echo -n "   waiting for floci :4566 (private)"
for _ in $(seq 1 40); do
  # 4566 is NOT host-exposed — poll it from inside the container network.
  if docker exec floci sh -c 'curl -sf -o /dev/null http://localhost:4566/ 2>/dev/null \
      || wget -q -O /dev/null http://localhost:4566/ 2>/dev/null'; then echo " ok"; break; fi
  echo -n "."; sleep 2
done

echo -n "   waiting for Floci UI :4500 (host-exposed)"
for _ in $(seq 1 40); do
  if curl -sf -o /dev/null "http://localhost:4500/" 2>/dev/null; then echo " ok"; break; fi
  echo -n "."; sleep 2
done

echo "== 2/3 deploying $CLIENT/$ENV AWS resources to floci-aws (in-container Terraform; dummy creds) =="
$FLOCI run --rm -T terraform init -input=false
$FLOCI run --rm -T terraform apply -auto-approve -input=false \
  -var "client_name=$CLIENT" -var "env=$ENV"

echo "== 3/3 bringing up the 009 backend/edge/frontend + console =="
CLIENTS="$CLIENT" bash sim/sim-up.sh

cat <<EOF

Console is up.
  Console (frontend):   http://localhost:${FRONTEND_PORT:-8080}/login
  Floci UI:             http://localhost:4500/
  Demo login:           company=$CLIENT user=alice password=${IDENTITY_SEED_PASSWORD:-demo-pass}
  Reference function:   Operations → Items (runs GET /api/operations/items via the edge)
  Smoke test:           bash sim/console/console-smoke.sh $CLIENT
  Tear down:            bash sim/console/console-down.sh
EOF
