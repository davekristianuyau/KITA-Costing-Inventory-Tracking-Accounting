#!/usr/bin/env bash
# T012: append a deployment audit record incl. the applied Release Set (FR-016).
# Usage: record-deployment.sh --client <c> --env <e> --provider <p> --status <ok|rolled-back|failed> --vars <tfvars>
set -euo pipefail

client="" env="" provider="" status="" vars=""
while [ $# -gt 0 ]; do
  case "$1" in
    --client) client="$2"; shift 2 ;;
    --env) env="$2"; shift 2 ;;
    --provider) provider="$2"; shift 2 ;;
    --status) status="$2"; shift 2 ;;
    --vars) vars="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done
[ -n "$client" ] && [ -n "$env" ] && [ -n "$status" ] || {
  echo "usage: record-deployment.sh --client <c> --env <e> --provider <p> --status <s> --vars <tfvars>" >&2
  exit 2
}

here="$(cd "$(dirname "$0")" && pwd)"
tf="$here/../infra/terraform"
log_dir="$tf/.deployments"
mkdir -p "$log_dir"
log="$log_dir/$client-$env.log"

# Extract "service=image:version" pairs from the tfvars Release Set for the audit line.
releases=""
if [ -n "$vars" ] && [ -f "$vars" ]; then
  releases="$(grep -oE '(image|version)\s*=\s*"[^"]+"' "$vars" | sed -E 's/.*"([^"]+)"/\1/' | paste - - -d: | tr '\n' ',' | sed 's/,$//')"
fi

ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "{\"ts\":\"$ts\",\"client\":\"$client\",\"env\":\"$env\",\"provider\":\"$provider\",\"status\":\"$status\",\"release_set\":\"$releases\"}" >>"$log"
echo "recorded $status for $client-$env"
