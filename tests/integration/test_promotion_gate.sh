#!/usr/bin/env bash
# T029: promote refuses a Release Set that isn't the one validated in STG (FR-011, SC-006).
# Offline: a version mismatch between stg/prod tfvars must be refused before any PROD change.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

client="promotest"
dir="$TF/environments/$client"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir"

write() { # <env> <version>
  cat >"$dir/$1.tfvars" <<EOF
cloud_provider = "aws"
client_name    = "$client"
env            = "$1"
region         = "us-east-1"
size           = "small"
db_backup_retention_days = 1
release_set = {
  gateway = { image = "ghcr.io/kita/gateway", version = "$2", visibility = "public", port = 8081, health_path = "/actuator/health" }
}
EOF
}

# PROD carries a different version than STG → must be refused (Gate 1, no cloud calls).
write stg 0.1.0
write prod 0.2.0
if "$ROOT/scripts/promote.sh" --client "$client" >/dev/null 2>&1; then
  die "promote accepted a PROD Release Set that differs from STG"
fi

# The STG-health gate (Gate 2) must also exist in promote.sh (exercised live).
grep -q "STG is not healthy" "$ROOT/scripts/promote.sh" || die "promote.sh lacks the STG-health gate"

pass "promote refuses a mismatched Release Set; STG-health gate present"
