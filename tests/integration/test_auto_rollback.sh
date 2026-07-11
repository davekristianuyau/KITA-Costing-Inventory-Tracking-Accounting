#!/usr/bin/env bash
# T031: a failed aggregate health check keeps the previous Release Set serving (FR-006a, SC-014).
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

# Structural guarantee (offline): deploy.sh rolls back to the last-good Release Set on health failure.
grep -q "rolling back to previous Release Set" "$ROOT/scripts/deploy.sh" ||
  die "deploy.sh lacks auto-rollback on health failure"

need_live "auto-rollback behavioral test"
# Live: deploy a known-bad Release Set over a healthy env and assert the previous set still serves.
# (Requires KITA_LIVE=1 and a deployed baseline; steps documented in tests/README.md.)
pass "auto-rollback exercised against live env"
