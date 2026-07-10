#!/usr/bin/env bash
# Provision + deploy a client's Release Set to {client}-{env}. Requires cloud credentials.
# Usage: deploy.sh --client <name> --env <stg|prod>
set -euo pipefail

client=""; env=""
while [ $# -gt 0 ]; do
  case "$1" in
    --client) client="$2"; shift 2 ;;
    --env)    env="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
[ -n "$client" ] && [ -n "$env" ] || { echo "usage: deploy.sh --client <name> --env <stg|prod>" >&2; exit 2; }

here="$(cd "$(dirname "$0")" && pwd)"
tf="$here/../infra/terraform"
vars="environments/$client/$env.tfvars"
provider="$(grep -oE '"(aws|gcp|azure)"' "$tf/$vars" | head -1 | tr -d '"')"
state_key="$client-$env"

"$here/validate-config.sh" --client "$client" --env "$env"

cd "$tf"
case "$provider" in
  aws)   terraform init -reconfigure -backend-config=backends/aws.tfbackend   -backend-config="key=$state_key/terraform.tfstate" ;;
  gcp)   terraform init -reconfigure -backend-config=backends/gcp.tfbackend   -backend-config="prefix=$state_key" ;;
  azure) terraform init -reconfigure -backend-config=backends/azure.tfbackend -backend-config="key=$state_key.tfstate" ;;
esac

terraform apply -auto-approve -var-file="$vars"

gw="$(terraform output -raw gateway_url)"
health="$(terraform output -raw aggregate_health_url)"
echo "deployed $state_key ($provider): $gw"
echo "checking aggregate health: $health"
curl -fsS "$health" >/dev/null && echo "healthy" || { echo "unhealthy — see rollback" >&2; exit 1; }
