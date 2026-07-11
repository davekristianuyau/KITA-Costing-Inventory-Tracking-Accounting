#!/usr/bin/env bash
# Shared helpers for deploy.sh / promote.sh. Requires cloud credentials for the tf_* functions.
set -euo pipefail

# Absolute path to infra/terraform, given the script's own dir.
tf_root() { cd "$1/../infra/terraform" && pwd; }

# Read the cloud provider from a tfvars file.
provider_of() { grep -oE '"(aws|gcp|azure)"' "$1" | head -1 | tr -d '"'; }

# terraform init against the right backend, with a per-environment state key {client}-{env}.
tf_init() {
  local provider="$1" key="$2"
  case "$provider" in
    aws) terraform init -reconfigure -backend-config=backends/aws.tfbackend -backend-config="key=$key/terraform.tfstate" ;;
    gcp) terraform init -reconfigure -backend-config=backends/gcp.tfbackend -backend-config="prefix=$key" ;;
    azure) terraform init -reconfigure -backend-config=backends/azure.tfbackend -backend-config="key=$key.tfstate" ;;
    *)
      echo "unsupported provider: $provider" >&2
      return 2
      ;;
  esac
}

# Poll the aggregate-health endpoint until healthy or timeout (seconds). Echoes nothing.
wait_healthy() {
  local url="$1" timeout="${2:-180}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if curl -fsS --max-time 15 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 10
    waited=$((waited + 10))
  done
  return 1
}

# The last-good Release Set snapshot for an environment (used for auto-rollback).
lastgood_file() { echo "$1/.last-good/$2.tfvars"; } # <tf_root> <state_key>
