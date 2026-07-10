#!/usr/bin/env bash
# T042: an unsupported cloud provider is rejected before any provisioning (FR-007).
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

client="ptest"
dir="$TF/environments/$client"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir"

cat >"$dir/stg.tfvars" <<EOF
cloud_provider = "digitalocean"
client_name    = "$client"
env            = "stg"
region         = "us-east-1"
size           = "small"
db_backup_retention_days = 1
release_set = {
  gateway = { image = "ghcr.io/kita/gateway", version = "0.1.0", visibility = "public", port = 8081, health_path = "/" }
}
EOF

if "$ROOT/scripts/validate-config.sh" --client "$client" --env stg >/dev/null 2>&1; then
  die "unsupported provider 'digitalocean' was accepted"
fi

# The provider enum is also enforced in the Terraform variable validation.
grep -qE 'contains\(\["aws", "gcp", "azure"\]' "$TF/variables.tf" ||
  grep -qE 'aws.*gcp.*azure' "$TF/variables.tf" ||
  die "variables.tf lacks a cloud_provider enum validation"

pass "unsupported provider rejected before provisioning"
