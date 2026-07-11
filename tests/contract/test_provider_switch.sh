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

# A ready-to-use platform overlay exists for each cloud — switching is picking a file, not editing one.
for c in aws gcp azure; do
  f="$TF/clouds/$c.tfvars"
  [ -f "$f" ] || die "missing platform overlay: clouds/$c.tfvars"
  grep -qE "^\s*cloud_provider\s*=\s*\"$c\"" "$f" || die "clouds/$c.tfvars must set cloud_provider = \"$c\""
done
pass "provider switch is config-only; overlays present; all outputs wired from aws/gcp/azure"
