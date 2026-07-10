#!/usr/bin/env bash
# T041: switching clouds is config-only (FR-003). The root wires all three modules behind
# var.cloud_provider and exposes an identical output surface from each.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

main="$TF/main.tf"
out="$TF/outputs.tf"

for c in aws gcp azure; do
  grep -qE "module \"$c\"" "$main" || die "root main.tf missing module block: $c"
  grep -qE "var.cloud_provider == \"$c\"" "$main" || die "root main.tf: $c not gated by cloud_provider"
done

# Every contract output must be fed from all three modules (one(concat(...))).
for o in "${MODULE_OUTPUTS[@]}"; do
  for c in aws gcp azure; do
    grep -qE "module\.$c\[\*\]\.$o" "$out" || die "root outputs.tf: $o not wired from module.$c"
  done
done
pass "provider switch is config-only; all outputs wired from aws/gcp/azure"
