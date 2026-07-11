#!/usr/bin/env bash
# Provision + deploy a client's Release Set to {client}-{env} on a chosen cloud, health-gated with
# auto-rollback. Requires cloud credentials.
# Usage: deploy.sh --client <name> --env <stg|prod> --cloud <aws|gcp|azure>
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
  echo "usage: deploy.sh --client <name> --env <stg|prod> --cloud <aws|gcp|azure>" >&2
  exit 2
}

here="$(cd "$(dirname "$0")" && pwd)"
. "$here/lib.sh"
tf="$(tf_root "$here")"
vars="environments/$client/$env.tfvars"
platform="clouds/$cloud.tfvars"
state_key="$client-$env"

"$here/validate-config.sh" --client "$client" --env "$env" --cloud "$cloud"

cd "$tf"
[ -f "$platform" ] || { echo "unknown platform: $platform" >&2; exit 1; }
tf_init "$cloud" "$state_key"

# Snapshot the previous (last-good) Release Set before changing anything, for auto-rollback.
lastgood="$(lastgood_file "$tf" "$state_key")"
mkdir -p "$(dirname "$lastgood")"

terraform apply -auto-approve -var-file="$vars" -var-file="$platform"

gw="$(terraform output -raw gateway_url)"
health="$(terraform output -raw aggregate_health_url)"
echo "deployed $state_key on $cloud: $gw"
echo "waiting for aggregate health: $health"

if wait_healthy "$health" 180; then
  echo "healthy"
  cp "$vars" "$lastgood" # this Release Set is now the known-good baseline
  "$here/record-deployment.sh" --client "$client" --env "$env" --provider "$cloud" --status ok --vars "$vars"
  exit 0
fi

# Unhealthy: roll back to the previous known-good Release Set if we have one (FR-006a, SC-014).
echo "aggregate health FAILED after deploy" >&2
if [ -f "$lastgood" ]; then
  echo "rolling back to previous Release Set: $lastgood" >&2
  terraform apply -auto-approve -var-file="$lastgood" -var-file="$platform"
  "$here/record-deployment.sh" --client "$client" --env "$env" --provider "$cloud" --status rolled-back --vars "$lastgood"
  echo "rolled back — previous Release Set is serving" >&2
else
  echo "no previous Release Set to roll back to (first deploy)" >&2
  "$here/record-deployment.sh" --client "$client" --env "$env" --provider "$cloud" --status failed --vars "$vars"
fi
exit 1
