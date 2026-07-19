#!/usr/bin/env bash
# Tear down the sim. Each project is removed independently WITH its volumes (FR-017): the edge first (it
# attaches to the client networks), then each client stack (which owns + removes its network + volumes).
set -euo pipefail
cd "$(dirname "$0")/.." # repo root

# `docker compose down` still parses the compose files, which reference required (`:?`) vars — supply
# harmless fallbacks so teardown never fails on a missing var (real values are irrelevant to `down`).
export CLIENT_ID="${CLIENT_ID:-client-a}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-x}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-x}"
export IDENTITY_DB_PASSWORD="${IDENTITY_DB_PASSWORD:-x}"
export IDENTITY_JWT_PRIVATE_KEY="${IDENTITY_JWT_PRIVATE_KEY:-x}"
export IDENTITY_JWT_PUBLIC_KEY="${IDENTITY_JWT_PUBLIC_KEY:-x}"
export IDENTITY_JWT_ENC_KEY="${IDENTITY_JWT_ENC_KEY:-x}"

CLIENTS="${CLIENTS:-client-a client-b}"

echo "== tearing down edge (project kita-edge) =="
docker compose -p kita-edge -f sim/docker-compose.edge.yml down -v --remove-orphans || true

for c in $CLIENTS; do
  echo "== tearing down client stack: $c (project kita-$c) =="
  # CLIENT_ID is only needed for interpolation; any value works for `down`.
  CLIENT_ID="$c" docker compose -p "kita-$c" \
    -f docker-compose.yml -f sim/client-overlay.yml down -v --remove-orphans || true
done

echo "Sim is down."
