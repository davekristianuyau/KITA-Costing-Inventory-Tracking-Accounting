#!/usr/bin/env bash
# Validate an environment's tfvars against the config-schema contract (fail-fast, pre-plan).
# Usage: validate-config.sh --client <name> --env <stg|prod>
set -euo pipefail

client=""; env=""
while [ $# -gt 0 ]; do
  case "$1" in
    --client) client="$2"; shift 2 ;;
    --env)    env="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
[ -n "$client" ] && [ -n "$env" ] || { echo "usage: validate-config.sh --client <name> --env <stg|prod>" >&2; exit 2; }

root="$(cd "$(dirname "$0")/../infra/terraform" && pwd)"
f="$root/environments/$client/$env.tfvars"
[ -f "$f" ] || { echo "config not found: $f" >&2; exit 1; }

fail() { echo "config invalid: $1" >&2; exit 1; }

grep -qE '^\s*cloud_provider\s*=\s*"(aws|gcp|azure)"' "$f" || fail "cloud_provider must be aws|gcp|azure"
grep -qE "^\s*env\s*=\s*\"$env\"" "$f" || fail "env in file must equal '$env'"
grep -qE "^\s*client_name\s*=\s*\"$client\"" "$f" || fail "client_name in file must equal '$client'"
grep -qE '^\s*client_name\s*=\s*"[a-z][a-z0-9-]{1,20}[a-z0-9]"' "$f" || fail "client_name must match naming rule"
grep -q 'visibility  *= *"public"' "$f" || grep -q 'visibility = "public"' "$f" || fail "at least one service must be public"
if grep -qE 'version\s*=\s*"latest"' "$f"; then fail "floating tag 'latest' is not allowed; pin an immutable version"; fi
grep -qiE 'password|secret|api[_-]?key' "$f" && fail "secrets must not appear in tfvars" || true

echo "config OK: $client/$env"
