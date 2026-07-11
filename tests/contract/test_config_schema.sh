#!/usr/bin/env bash
# T014: validate-config.sh accepts a valid (cloud-agnostic) env config against a real platform
# overlay, and rejects floating tags, no-public services, and secrets. Uses a throwaway client
# under environments/, cleaned up after.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

client="citest"
dir="$TF/environments/$client"
trap 'rm -rf "$dir"' EXIT
mkdir -p "$dir"

write_cfg() { cat >"$dir/stg.tfvars"; }
expect() { # <should_pass 0|1> <label>
  if "$ROOT/scripts/validate-config.sh" --client "$client" --env stg --cloud aws >/dev/null 2>&1; then rc=0; else rc=1; fi
  [ "$rc" = "$1" ] || die "$2 (validate-config exit $rc, expected $1)"
}

valid_body='
release_set = {
  gateway = { image = "ghcr.io/kita/gateway", version = "0.1.0", visibility = "public", port = 8081, health_path = "/actuator/health" }
  operations-service = { image = "ghcr.io/kita/operations-service", version = "0.1.0", visibility = "private", port = 8083, health_path = "/actuator/health" }
}'

write_cfg <<EOF
client_name    = "$client"
env            = "stg"
size           = "small"
db_backup_retention_days = 1
$valid_body
EOF
expect 0 "valid config should pass"

# floating tag 'latest' rejected
write_cfg <<EOF
client_name    = "$client"
env            = "stg"
size           = "small"
db_backup_retention_days = 1
release_set = {
  gateway = { image = "ghcr.io/kita/gateway", version = "latest", visibility = "public", port = 8081, health_path = "/" }
}
EOF
expect 1 "floating tag 'latest' should be rejected"

# no public service rejected
write_cfg <<EOF
client_name    = "$client"
env            = "stg"
size           = "small"
db_backup_retention_days = 1
release_set = {
  operations-service = { image = "ghcr.io/kita/operations-service", version = "0.1.0", visibility = "private", port = 8083, health_path = "/" }
}
EOF
expect 1 "config with no public service should be rejected"

# secret in tfvars rejected
write_cfg <<EOF
client_name    = "$client"
env            = "stg"
size           = "small"
db_backup_retention_days = 1
db_password    = "hunter2"
$valid_body
EOF
expect 1 "secret in tfvars should be rejected"

pass "config-schema: accepts valid env config, rejects latest/no-public/secret"
