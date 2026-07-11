#!/usr/bin/env bash
# T042: an unsupported cloud platform is rejected before any provisioning (FR-007).
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

client="ptest"
dir="$TF/environments/$client"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir"

cat >"$dir/stg.tfvars" <<EOF
client_name    = "$client"
env            = "stg"
size           = "small"
db_backup_retention_days = 1
release_set = {
  gateway = { image = "ghcr.io/kita/gateway", version = "0.1.0", visibility = "public", port = 8081, health_path = "/" }
}
EOF

# Selecting a platform with no overlay (clouds/digitalocean.tfvars) must be rejected.
if "$ROOT/scripts/validate-config.sh" --client "$client" --env stg --cloud digitalocean >/dev/null 2>&1; then
  die "unsupported platform 'digitalocean' was accepted"
fi

# The provider enum is also enforced in the Terraform variable validation.
grep -qE 'contains\(\["aws", "gcp", "azure"\]' "$TF/variables.tf" ||
  die "variables.tf lacks a cloud_provider enum validation"

pass "unsupported platform rejected before provisioning"
