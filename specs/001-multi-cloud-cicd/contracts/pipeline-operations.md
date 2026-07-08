# Contract: Pipeline Operations

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

The three CI/CD operations and their pre/postconditions. Each is a GitHub Actions workflow
delegating to a provider-agnostic script. Every operation writes a Deployment Record (FR-016).

## Operation: deploy (STG)

- **Workflow**: `.github/workflows/deploy-stg.yml` — trigger: merge to `main` (auto).
- **Script**: `scripts/deploy.sh --client <name> --env stg`
- **Preconditions**:
  - Config file `environments/<client>/stg.tfvars` passes the config-schema contract.
  - Quality gates pass: `terraform fmt -check`, `validate`, `tflint`, `plan` succeeds.
  - Required cloud credentials present in the STG-scoped CI secret store.
- **Steps**: init (state key `<client>-stg`) → plan → apply → wait for `health_check_url` →
  run integration smoke test (health + DB read/write + isolation).
- **Postconditions**:
  - App healthy at `app_url`; smoke test green → outcome `success`.
  - Any step fails → pipeline stops, no success reported, prior healthy version still serving
    (FR-006/FR-006a); outcome `failed` or `rolled_back` with reason.
- **Never** touches PROD (FR-009, SC-007).

## Operation: promote (STG → PROD)

- **Workflow**: `.github/workflows/promote-prod.yml` — trigger: manual `workflow_dispatch`,
  gated by a protected GitHub Environment requiring approval (FR-010).
- **Script**: `scripts/promote.sh --client <name> --version <app_version>`
- **Preconditions (GATE)**:
  - The exact `app_version` is **currently deployed and healthy in `<client>-stg`** — else
    the operation is refused before any change (FR-011, SC-006).
  - `environments/<client>/prod.tfvars` passes the config-schema contract (prod backup rules).
  - Approver recorded via the environment approval (FR-010).
- **Steps**: verify STG gate → init (state key `<client>-prod`) → plan (only image/version
  and prod sizing differ) → apply with health-gated rollout → smoke test.
- **Postconditions**:
  - PROD serves the same `app_version` proven in STG, healthy → outcome `success`.
  - Failed health check → auto-rollback to previous PROD version; failed version gets no
    traffic (FR-006a); outcome `rolled_back`.
  - No rebuild of the artifact occurs (same digest as STG) (R7).

## Operation: teardown

- **Workflow**: `.github/workflows/teardown.yml` — trigger: manual `workflow_dispatch`
  (requires typing the target `<client>-<env>` to confirm).
- **Script**: `scripts/teardown.sh --client <name> --env <stg|prod>`
- **Preconditions**: explicit confirmation of the exact environment name; credentials present.
- **Steps**: init target state → `terraform destroy` → verify via `resource_ids` that no
  resource remains.
- **Postconditions**: 100% of the environment's resources removed and confirmed (FR-015,
  SC-009); the other tier and other clients are unaffected (SC-008); outcome recorded.

## Cross-operation guarantees

- **Fail-fast**: any failed provisioning/deploy/quality step halts the operation and reports
  the failing step + reason; no partial success is ever reported as success (FR-006, SC-004).
- **Concurrency**: remote-state locking prevents two operations mutating the same
  `<client>-<env>` state at once (FR-017); a second run waits or fails cleanly.
- **Secret hygiene**: scripts never echo secret values; `TF_LOG` scrubbing and masked CI
  secrets ensure nothing leaks to logs (FR-013, SC-010).
- **Audit**: each run appends a Deployment Record (operation, target, version, actor,
  timestamp, outcome) (FR-016).
