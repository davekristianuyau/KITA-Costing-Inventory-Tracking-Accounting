#!/usr/bin/env bash
# T035: promote the STG-validated Release Set to PROD on a chosen cloud. Two gates before any PROD
# change: (1) PROD Release Set must be identical to STG's (same image versions — no rebuild), and
# (2) STG must currently be healthy. Only then apply PROD (via deploy.sh — health-gated + rollback).
# Usage: promote.sh --client <name> --cloud <aws|gcp|azure>
set -euo pipefail

client="" cloud=""
while [ $# -gt 0 ]; do
  case "$1" in
    --client) client="$2"; shift 2 ;;
    --cloud) cloud="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
[ -n "$client" ] && [ -n "$cloud" ] || { echo "usage: promote.sh --client <name> --cloud <aws|gcp|azure>" >&2; exit 2; }

here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
tf="$(tf_root "$here")"
cd "$tf"

stg="environments/$client/stg.tfvars"
prod="environments/$client/prod.tfvars"
platform="clouds/$cloud.tfvars"
[ -f "$stg" ] || { echo "missing $stg" >&2; exit 1; }
[ -f "$prod" ] || { echo "missing $prod — create it before promoting" >&2; exit 1; }
[ -f "$platform" ] || { echo "unknown platform: $platform" >&2; exit 1; }

release_set_of() {
  grep -oE '(image|version)[[:space:]]*=[[:space:]]*"[^"]+"' "$1" |
    sed -E 's/.*"([^"]+)"/\1/' | paste - - -d: | sort
}

# Gate 1: the PROD Release Set must match the one validated in STG (no rebuild between tiers).
if ! diff <(release_set_of "$stg") <(release_set_of "$prod") >/dev/null; then
  echo "PROMOTE REFUSED: PROD Release Set differs from STG. Promote the exact versions validated in STG." >&2
  exit 1
fi

# Gate 2: STG must be healthy right now.
tf_init "$cloud" "$client-stg" >/dev/null
stg_health="$(terraform output -raw aggregate_health_url 2>/dev/null || true)"
if [ -z "$stg_health" ] || ! curl -fsS --max-time 15 "$stg_health" >/dev/null 2>&1; then
  echo "PROMOTE REFUSED: STG is not healthy (or not deployed); cannot promote to PROD." >&2
  exit 1
fi
echo "gates passed: STG healthy + Release Set matches — promoting to PROD on $cloud"

exec "$here/deploy.sh" --client "$client" --env prod --cloud "$cloud"
