#!/usr/bin/env bash
# Shared assertions for KITA infra contract tests. No cloud credentials required.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF="$ROOT/infra/terraform"

pass() { echo "PASS: $1"; }
die() {
  echo "FAIL: $1" >&2
  exit 1
}
skip() {
  echo "SKIP: $1"
  exit 0
}
# Integration tests that need a live deployment are skipped unless KITA_LIVE=1.
need_live() { [ "${KITA_LIVE:-0}" = "1" ] || skip "$1 — set KITA_LIVE=1 against a deployed env to run"; }

# The module-interface contract: every per-cloud module declares these exact names.
MODULE_INPUTS=(client_name env region release_set size custom_domain db_backup_retention_days tags)
MODULE_OUTPUTS=(gateway_url aggregate_health_url service_endpoints db_connection_secret_ref object_storage_ref environment_name resource_ids)

assert_module_interface() {
  local mod="$1" name="$2" i o
  [ -d "$mod" ] || die "$name module dir not found: $mod"
  for i in "${MODULE_INPUTS[@]}"; do
    grep -rqE "variable \"$i\"" "$mod" || die "$name module missing input: $i"
  done
  for o in "${MODULE_OUTPUTS[@]}"; do
    grep -rqE "output \"$o\"" "$mod" || die "$name module missing output: $o"
  done
  pass "$name module declares all ${#MODULE_INPUTS[@]} inputs + ${#MODULE_OUTPUTS[@]} outputs"
}
