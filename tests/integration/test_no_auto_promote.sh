#!/usr/bin/env bash
# T030: an STG deploy/merge never changes PROD (FR-009, SC-007).
# The STG workflow only deploys STG; promotion to PROD is a separate, manual, gated action.
set -euo pipefail
. "$(cd "$(dirname "$0")/../.." && pwd)/tests/lib.sh"

wf="$ROOT/.github/workflows"
stg="$wf/deploy-stg.yml"
prod="$wf/promote-prod.yml"
[ -f "$stg" ] || die "missing deploy-stg workflow"
[ -f "$prod" ] || die "missing promote-prod workflow"

# STG workflow deploys stg and never calls promote or targets prod.
grep -qE '\-\-env stg' "$stg" || die "deploy-stg does not deploy the stg env"
grep -q 'promote.sh' "$stg" && die "deploy-stg must not promote to PROD"
grep -qE '\-\-env prod' "$stg" && die "deploy-stg must not target the prod env"

# Promotion is manual only (workflow_dispatch), not triggered by push/merge.
grep -q 'workflow_dispatch' "$prod" || die "promote-prod must be manual (workflow_dispatch)"
grep -qE '^on:|  push:' "$prod" && grep -q 'push:' "$prod" && die "promote-prod must not trigger on push"

pass "STG deploy never changes PROD; promotion is separate and manual"
