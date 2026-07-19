# Contract — CI jobs (`.github/workflows/ci.yml`)

## `infra` — BLOCKING gate (fixed)

- Runs: `terraform fmt -check -recursive`, `terraform validate`, `tflint --recursive`, infra contract tests.
- **Must pass** on `main` and every branch after the D6 fix (added `required_version`/`required_providers` to
  the flagged 001 modules). SC-001.
- Still **fails** when a genuine format/validity error is introduced (US1 AC-2) — the gate stays meaningful.
- Terraform pinned to `1.9.8` (matches the deploy runner — FR-010).

## `cloud-deploy` — NON-BLOCKING job (new)

- `continue-on-error: true` (mirrors feature 009's `sim-smoke`). Does not block merges.
- Runs `bash sim/cloud-deploy/run-all.sh` → for each of aws/gcp/azure: probe (or use committed coverage map) →
  `deploy-check.sh` (apply → verify → destroy) against Floci, dummy creds only.
- Reports per-cloud pass/fail; overall completes < 15 min (SC-004).
- Uses the runner's Docker daemon (as the 009 sim-smoke job does); no cloud credentials configured.

## Acceptance

- A push shows `infra` green and required; `cloud-deploy` present as a separate, non-blocking check.
- A change that breaks a cloud's Terraform deployment turns `cloud-deploy` red (SC-005) while `infra` (validate-
  only) may still pass — demonstrating the added deploy signal.
