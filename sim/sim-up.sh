#!/usr/bin/env bash
# Bring up the whole local model (US3): two isolated client stacks (feature-008, one Compose project each)
# + the shared edge project (identity + edge + frontend). Only the frontend is host-exposed.
set -euo pipefail
cd "$(dirname "$0")/.." # repo root

# Base env (DB creds etc.) if present.
if [ -f .env ]; then set -a; . ./.env; set +a; fi

# Shared token keys: generate once into sim/.env.keys (gitignored), then load.
if [ -z "${IDENTITY_JWT_PRIVATE_KEY:-}" ]; then
  if [ ! -f sim/.env.keys ]; then
    echo "== generating shared session keys → sim/.env.keys =="
    bash sim/gen-keys.sh > sim/.env.keys
  fi
  set -a; . sim/.env.keys; set +a
fi

# Sim-local credential defaults (dev only; production uses each cloud's secret store).
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-kita}"
export DATABASE_USER="${DATABASE_USER:-kita}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-$POSTGRES_PASSWORD}"
export IDENTITY_DB_PASSWORD="${IDENTITY_DB_PASSWORD:-identity}"
export IDENTITY_SEED_PASSWORD="${IDENTITY_SEED_PASSWORD:-demo-pass}"
export FRONTEND_PORT="${FRONTEND_PORT:-8080}"

CLIENTS="${CLIENTS:-client-a client-b}"

for c in $CLIENTS; do
  echo "== bringing up client stack: $c (project kita-$c) =="
  CLIENT_ID="$c" docker compose -p "kita-$c" \
    -f docker-compose.yml -f sim/client-overlay.yml up -d --build
done

echo "== bringing up edge (identity + edge + frontend) =="
docker compose -p kita-edge -f sim/docker-compose.edge.yml up -d --build

cat <<EOF

Sim is up.
  Frontend (only host-exposed):  http://localhost:${FRONTEND_PORT}/login
  Demo logins:                   company=client-a user=alice / company=client-b user=bob
                                 password=${IDENTITY_SEED_PASSWORD}
  Smoke test:                    bash sim/sim-smoke.sh
  Tear down:                     bash sim/sim-down.sh
EOF
