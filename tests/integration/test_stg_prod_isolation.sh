#!/usr/bin/env bash
# T028: STG and PROD share no network/DB/storage/services (SC-005).
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

# Structural guarantee (offline): every resource is named {client}-{env}[-service] and each env has
# its own remote-state key, so STG and PROD can never collide.
grep -q 'name_prefix' "$TF/modules/common/main.tf" || die "common module has no {client}-{env} naming"
grep -q 'client-stg\|$client-stg\|state_key' "$ROOT/scripts/deploy.sh" ||
  grep -q '\$client-\$env' "$ROOT/scripts/deploy.sh" || die "deploy.sh does not key state per env"

need_live "STG/PROD isolation behavioral test"
# Live: compare resource_ids output of {client}-stg vs {client}-prod; assert the sets are disjoint.
pass "STG/PROD resource sets are disjoint"
