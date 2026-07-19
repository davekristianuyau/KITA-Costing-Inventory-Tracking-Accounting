#!/usr/bin/env bash
# Tear down the sim. Each project is removed independently WITH its volumes (FR-017): the edge first (it
# attaches to the client networks), then each client stack (which owns + removes its network + volumes).
set -euo pipefail
cd "$(dirname "$0")/.." # repo root

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
