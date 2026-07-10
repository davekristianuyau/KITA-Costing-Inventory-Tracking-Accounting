# Contract: Pipeline Operations (multi-service Release Set)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

The three CI/CD operations, updated to deploy/promote a **Release Set** (the coordinated set of
service image versions). Each operation writes a Deployment Record incl. the Release Set (FR-016).

## Operation: deploy (STG)
- **Workflow**: `.github/workflows/deploy-stg.yml` — trigger: merge to `main` (auto).
- **Script**: `scripts/deploy.sh --client <name> --env stg`
- **Preconditions**: `environments/<client>/stg.tfvars` passes the config-schema contract; quality
  gates (fmt/validate/tflint/plan) pass; STG-scoped credentials present.
- **Steps**: init (state key `<client>-stg`) → plan → apply (provision network, per-service compute
  for every Release-Set entry, shared DB, storage, public gateway) → wait for **aggregate health** →
  smoke test (public gateway reachable; backend services NOT public; gateway→service round trip).
- **Postconditions**: all services healthy behind the gateway → `success`. Any step fails → stop,
  no success reported, previous Release Set still serving; auto-rollback (`rolled_back`) or `failed`
  with reason. **Never** touches PROD (FR-009, SC-007).

## Operation: promote (STG → PROD)
- **Workflow**: `.github/workflows/promote-prod.yml` — manual `workflow_dispatch`, gated by a
  protected GitHub Environment approval (FR-010).
- **Script**: `scripts/promote.sh --client <name> --release-set <ref-or-stg>`
- **Preconditions (GATE)**: the exact Release Set is **currently deployed and healthy in
  `<client>-stg`** (FR-011, SC-006); `prod.tfvars` passes the contract (prod backup rules); approver
  recorded.
- **Steps**: verify STG gate → init (`<client>-prod`) → plan (only image versions + prod sizing
  differ) → apply per-service with health-gated rollout → aggregate-health smoke test.
- **Postconditions**: PROD serves the **same Release Set** proven in STG (no rebuild). Failed
  aggregate health → auto-rollback to the previous PROD Release Set (FR-006a); failed versions get
  no traffic.

## Operation: teardown
- **Workflow**: `.github/workflows/teardown.yml` — manual, requires typing `<client>-<env>` to confirm.
- **Script**: `scripts/teardown.sh --client <name> --env <stg|prod>`
- **Steps**: init target state → `terraform destroy` → verify via `resource_ids` nothing remains.
- **Postconditions**: 100% of the environment's resources (all services, DB, storage, network,
  gateway) removed and confirmed (FR-015, SC-009); other tier/clients unaffected (SC-008).

## Cross-operation guarantees
- **Fail-fast**: any failed provisioning/deploy/quality step halts; no partial success reported (FR-006, SC-004).
- **Concurrency**: remote-state locking prevents concurrent mutation of the same `<client>-<env>` (FR-017).
- **Secret hygiene**: no secret values in logs/output; masked CI secrets + scrubbed TF logs (FR-013, SC-010).
- **Version consistency**: deploy/promote/rollback act on the **whole Release Set** — an environment
  never mixes versions outside a declared set (FR-022).
- **Audit**: each run appends a Deployment Record (operation, target, tier, Release Set, actor,
  timestamp, outcome) (FR-016).
