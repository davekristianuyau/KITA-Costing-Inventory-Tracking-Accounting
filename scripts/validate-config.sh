#!/usr/bin/env bash
# Validate an environment's config against the config-schema contract (fail-fast, pre-plan).
# Cloud comes from a platform overlay (clouds/<cloud>.tfvars); the env file is cloud-agnostic.
# Usage: validate-config.sh --client <name> --env <stg|prod> --cloud <aws|gcp|azure>
set -euo pipefail

client="" env="" cloud=""
while [ $# -gt 0 ]; do
  case "$1" in
    --client) client="$2"; shift 2 ;;
    --env) env="$2"; shift 2 ;;
    --cloud) cloud="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
[ -n "$client" ] && [ -n "$env" ] && [ -n "$cloud" ] || {
  echo "usage: validate-config.sh --client <name> --env <stg|prod> --cloud <aws|gcp|azure>" >&2
  exit 2
}

root="$(cd "$(dirname "$0")/../infra/terraform" && pwd)"
f="$root/environments/$client/$env.tfvars"
p="$root/clouds/$cloud.tfvars"
[ -f "$f" ] || { echo "config not found: $f" >&2; exit 1; }
[ -f "$p" ] || { echo "unknown platform '$cloud' (no $p)" >&2; exit 1; }

fail() { echo "config invalid: $1" >&2; exit 1; }

# Platform overlay: must declare a supported cloud_provider matching the selection.
grep -qE "^\s*cloud_provider\s*=\s*\"$cloud\"" "$p" || fail "clouds/$cloud.tfvars must set cloud_provider = \"$cloud\""
grep -qE '^\s*cloud_provider\s*=\s*"(aws|gcp|azure)"' "$p" || fail "cloud_provider must be aws|gcp|azure"

# Env file: cloud-agnostic client/env/release-set rules.
grep -qE "^\s*env\s*=\s*\"$env\"" "$f" || fail "env in file must equal '$env'"
grep -qE "^\s*client_name\s*=\s*\"$client\"" "$f" || fail "client_name in file must equal '$client'"
grep -qE '^\s*client_name\s*=\s*"[a-z][a-z0-9-]{1,20}[a-z0-9]"' "$f" || fail "client_name must match naming rule"
grep -q 'visibility  *= *"public"' "$f" || grep -q 'visibility = "public"' "$f" || fail "at least one service must be public"
if grep -qE 'version\s*=\s*"latest"' "$f"; then fail "floating tag 'latest' is not allowed; pin an immutable version"; fi
grep -qiE 'password|secret|api[_-]?key' "$f" && fail "secrets must not appear in tfvars" || true

echo "config OK: $client/$env on $cloud"
