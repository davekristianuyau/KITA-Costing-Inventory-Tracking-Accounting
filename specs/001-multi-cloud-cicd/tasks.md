---

description: "Task list for Multi-Cloud CI/CD Infrastructure Scaffolding"
---

# Tasks: Multi-Cloud CI/CD Infrastructure Scaffolding

**Input**: Design documents from `/specs/001-multi-cloud-cicd/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED. The constitution's Principle II (Test-Driven Development,
NON-NEGOTIABLE) and the plan's Testing section require contract/validation tests and
post-deploy smoke tests. Test tasks are written first and MUST fail before implementation.

**Organization**: Tasks are grouped by user story. Reference cloud for the MVP is **AWS**
(US1/US2); GCP and Azure modules arrive in US3 to prove portability.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US5 (from spec.md)

## Path Conventions

Infrastructure + pipeline layout (from plan.md): `infra/terraform/`, `scripts/`, `tests/`,
`.github/workflows/`. All paths are repository-relative.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository scaffolding and tooling

- [ ] T001 Create directory structure per plan.md: `infra/terraform/{modules/{common,aws,gcp,azure},backends,environments}`, `scripts/`, `tests/{contract,integration,fixtures}`, `.github/workflows/`
- [ ] T002 Create `infra/terraform/versions.tf` pinning Terraform >= 1.9 and providers `hashicorp/aws` ~> 5.x, `hashicorp/google` ~> 5.x, `hashicorp/azurerm` ~> 4.x
- [ ] T003 [P] Add `.tflint.hcl` and a `terraform fmt`/`validate` config at repo root for linting
- [ ] T004 [P] Create a lightweight test runner `tests/run.sh` that executes contract tests (bash assertions over `terraform` output) and reports pass/fail
- [ ] T005 [P] Add `.gitignore` entries for `.terraform/`, `*.tfstate*`, `*.tfvars` secrets, and local plan files

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The provider-abstraction contract, config schema, common module, and state
backends that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T006 Create `infra/terraform/variables.tf` implementing the config contract from contracts/config-schema.md (cloud_provider, client_name, env, app_image, app_version, region, custom_domain, size, db_backup_retention_days, tags) with `validation` blocks (enums, client_name regex, immutable app_version, prod backup rule)
- [ ] T007 Create `infra/terraform/main.tf` root that selects exactly one per-cloud module via `var.cloud_provider` using conditional `count`/module blocks, passing the module-interface inputs from contracts/module-interface.md
- [ ] T008 Create `infra/terraform/outputs.tf` exposing common outputs (app_url, health_check_url, db_connection_secret_ref, object_storage_ref, environment_name, resource_ids) sourced from the selected module
- [ ] T009 [P] Implement `infra/terraform/modules/common/` with naming locals (`{client_name}-{env}`), mandatory tags/labels (`client`, `env`, `managed_by=terraform`), and shared validation helpers (FR-020, FR-021)
- [ ] T010 [P] Create `scripts/validate-config.sh` enforcing every rule in contracts/config-schema.md (required vars, enums, regex, path/value agreement, no floating tags, no secret-like keys) — fail-fast with clear messages (FR-006, FR-007, FR-013)
- [ ] T011 [P] Create remote-state backend configs `infra/terraform/backends/{aws,gcp,azure}.tfbackend` with state key pattern `{client}-{env}` and locking (S3+DynamoDB / GCS versioned / Azure blob lease) per research.md R5
- [ ] T012 [P] Implement a Deployment Record helper `scripts/record-deployment.sh` that appends an audit entry (operation, target, tier, version, actor, timestamp, outcome) per data-model.md (FR-016)
- [ ] T013 [P] Document one-time state-backend bootstrap steps in `infra/terraform/backends/README.md`

**Checkpoint**: Contract + config layer ready — cloud modules and pipeline can be built.

---

## Phase 3: User Story 1 - Deploy to a chosen cloud (Priority: P1) 🎯 MVP

**Goal**: From a single pipeline run, provision the full stack on the reference cloud (AWS)
and deploy the app so it is publicly reachable over TLS, connected to its database, healthy.

**Independent Test**: Run `scripts/deploy.sh --client <test> --env stg` against AWS; the
app_url `/health` returns healthy, a DB read/write smoke test passes, and a re-run reports
zero changes.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [ ] T014 [P] [US1] Contract test `tests/contract/test_aws_module_interface.sh` asserting `modules/aws` declares exactly the inputs/outputs of contracts/module-interface.md
- [ ] T015 [P] [US1] Contract test `tests/contract/test_config_schema.sh` asserting `validate-config.sh` accepts a valid fixture and rejects bad provider/env/regex/floating-tag/secret cases
- [ ] T016 [P] [US1] Integration smoke test `tests/integration/test_deploy_health.sh` asserting a deployed env answers `/health` and performs a DB read/write
- [ ] T017 [P] [US1] Idempotency test `tests/integration/test_idempotent_apply.sh` asserting a second apply on unchanged config yields no resource changes (FR-005, SC-003)
- [ ] T018 [P] [US1] Add AWS fixture `tests/fixtures/aws-stg.tfvars` for a throwaway test client

### Implementation for User Story 1

- [ ] T019 [US1] Implement AWS networking in `infra/terraform/modules/aws/network.tf` (isolated VPC, subnets, security groups) using common naming (FR-001, FR-008)
- [ ] T020 [US1] Implement AWS managed PostgreSQL in `infra/terraform/modules/aws/database.tf` (RDS, single node, encryption at rest, credentials written to Secrets Manager) (FR-014, FR-013)
- [ ] T021 [US1] Implement AWS object storage in `infra/terraform/modules/aws/storage.tf` (S3, encrypted) (FR-014)
- [ ] T022 [US1] Implement AWS compute + ingress in `infra/terraform/modules/aws/compute.tf` (ECS Fargate service, ALB, HTTPS listener, ACM cert, `/health` check, custom_domain support) (FR-001, FR-001a, FR-001b)
- [ ] T023 [US1] Implement AWS module `variables.tf`/`outputs.tf` in `infra/terraform/modules/aws/` matching the module-interface contract exactly (makes T014 pass)
- [ ] T024 [US1] Wire `modules/aws` into root `main.tf` selection path and confirm `validate-config.sh` gate runs before plan
- [ ] T025 [US1] Create `scripts/deploy.sh --client --env` (init with `{client}-{env}` state key → validate-config → plan → apply → wait health → run smoke test → record deployment) (contracts/pipeline-operations.md)
- [ ] T026 [US1] Ensure structured logs are shipped to CloudWatch and secret values are scrubbed from all script/plan output (FR-018, FR-013, SC-010)

**Checkpoint**: A full KITA stack deploys to AWS from one command and is independently testable. **MVP reached.**

---

## Phase 4: User Story 2 - Separate STG and PROD with gated promotion (Priority: P1)

**Goal**: Two isolated tiers (STG, PROD) per deployment with per-env sizing; promote the
same validated artifact STG→PROD as a deliberate, gated action; auto-rollback on failure.

**Independent Test**: Deploy a version to STG (healthy, isolated); promote it to PROD and
confirm PROD serves the same version; attempt promoting an un-STG'd version and confirm it
is blocked; confirm STG and PROD share no DB/storage.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [ ] T027 [P] [US2] Test `tests/integration/test_stg_prod_isolation.sh` asserting STG and PROD share no network/DB/storage (FR-008, SC-005)
- [ ] T028 [P] [US2] Test `tests/contract/test_promotion_gate.sh` asserting `promote.sh` refuses a version not healthy in STG (FR-011, SC-006)
- [ ] T029 [P] [US2] Test `tests/integration/test_no_auto_promote.sh` asserting a STG deploy/merge never changes PROD (FR-009, SC-007)
- [ ] T030 [P] [US2] Test `tests/integration/test_auto_rollback.sh` asserting a failed health check leaves the previous version serving (FR-006a)

### Implementation for User Story 2

- [ ] T031 [US2] Add `size` sizing profiles (small/standard/large) to `modules/aws` and default STG→small, PROD→standard without changing architecture (FR-008a)
- [ ] T032 [US2] Enable PROD daily backups + point-in-time recovery in `modules/aws/database.tf`, gated on `env == prod` and `db_backup_retention_days` (FR-014a)
- [ ] T033 [US2] Implement health-gated rollout + auto-rollback in `modules/aws/compute.tf` (ECS blue/green or rolling with deployment circuit breaker) (FR-006a) — makes T030 pass
- [ ] T034 [US2] Create `scripts/promote.sh --client --version` enforcing the STG-healthy gate, then applying PROD with the same immutable digest (no rebuild) and health-gated rollout (contracts/pipeline-operations.md) — makes T028 pass
- [ ] T035 [P] [US2] Create `.github/workflows/deploy-stg.yml` (auto on merge to main → deploy.sh env=stg → smoke test)
- [ ] T036 [P] [US2] Create `.github/workflows/promote-prod.yml` (manual `workflow_dispatch`, protected GitHub Environment approval → promote.sh) enforcing non-automatic promotion (FR-010)
- [ ] T037 [US2] Scope STG vs PROD secrets separately in the secret store and CI environment secrets (FR-013)

**Checkpoint**: STG/PROD lifecycle with gated promotion and auto-rollback works on AWS.

---

## Phase 5: User Story 3 - Portability across clouds with minimal changes (Priority: P2)

**Goal**: Deploy the same app to GCP and Azure by changing only configuration; reject
unsupported providers.

**Independent Test**: Take a working AWS config, change only `cloud_provider` (+ provider
settings), deploy to GCP then Azure, and confirm equivalent healthy state with no pipeline
or app changes.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [ ] T038 [P] [US3] Contract test `tests/contract/test_gcp_module_interface.sh` (gcp module matches module-interface)
- [ ] T039 [P] [US3] Contract test `tests/contract/test_azure_module_interface.sh` (azure module matches module-interface)
- [ ] T040 [P] [US3] Test `tests/integration/test_provider_switch.sh` asserting only config changes are needed to switch providers (FR-003, SC-002)
- [ ] T041 [P] [US3] Test `tests/contract/test_invalid_provider.sh` asserting an unsupported provider is rejected before provisioning (FR-007)
- [ ] T042 [P] [US3] Add GCP + Azure fixtures `tests/fixtures/gcp-stg.tfvars`, `tests/fixtures/azure-stg.tfvars`

### Implementation for User Story 3

- [ ] T043 [P] [US3] Implement `infra/terraform/modules/gcp/` (Cloud Run, Cloud SQL PostgreSQL, GCS, Secret Manager, Google-managed cert, health-gated revisions) to the module-interface contract (research.md R2–R5)
- [ ] T044 [P] [US3] Implement `infra/terraform/modules/azure/` (Container Apps, Azure DB for PostgreSQL Flexible, Blob, Key Vault, managed cert, revision traffic weights) to the module-interface contract
- [ ] T045 [US3] Extend root `main.tf` selection to route gcp/azure and confirm all three modules share the identical output surface
- [ ] T046 [US3] Add GCP and Azure remote-state backends wiring in `scripts/deploy.sh`/`promote.sh` (state key `{client}-{env}` per provider)
- [ ] T047 [US3] Verify region policy (single default, per-client override, in-region data) applies uniformly across all three modules (FR-002a)

**Checkpoint**: Any of the three clouds is selectable with config-only changes.

---

## Phase 6: User Story 4 - Isolated per-client environments (Priority: P3)

**Goal**: Provision dedicated, isolated STG+PROD environments per client with no shared
data path between clients.

**Independent Test**: Provision two client deployments on one provider; confirm each has its
own network/DB/storage across both tiers and that tearing one down does not affect the other.

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [ ] T048 [P] [US4] Test `tests/integration/test_client_isolation.sh` asserting two clients share no network/DB/storage across tiers (FR-012, SC-008)
- [ ] T049 [P] [US4] Test `tests/contract/test_naming_convention.sh` asserting 100% of resources follow `{client-name}-{env}` and carry client/env tags (FR-020, FR-021, SC-012)

### Implementation for User Story 4

- [ ] T050 [US4] Establish `infra/terraform/environments/<client-name>/{stg,prod}.tfvars` convention and an example client dir; document in quickstart flow
- [ ] T051 [US4] Ensure per-client state key isolation (`{client}-{env}`) and mandatory client/env tags are enforced in `modules/common` and all three modules (FR-020) — makes T049 pass
- [ ] T052 [P] [US4] Create `scripts/onboard-client.sh` that scaffolds a client's tfvars from the config-schema example and runs `validate-config.sh`

**Checkpoint**: Multiple isolated client deployments coexist safely.

---

## Phase 7: User Story 5 - Lifecycle management (Priority: P3)

**Goal**: Update a running deployment to a new version and fully tear down an environment,
reclaiming all resources.

**Independent Test**: Deploy a version, update to a newer version (new version serves
traffic), then tear the environment down and confirm no billable resources remain.

### Tests for User Story 5 (write first, must FAIL) ⚠️

- [ ] T053 [P] [US5] Test `tests/integration/test_version_update.sh` asserting a new app_version becomes active and healthy (FR-015)
- [ ] T054 [P] [US5] Test `tests/integration/test_teardown_complete.sh` asserting teardown removes 100% of an env's resources via `resource_ids` and leaves other tiers/clients intact (FR-015, SC-009, SC-008)

### Implementation for User Story 5

- [ ] T055 [US5] Create `scripts/teardown.sh --client --env` (init target state → `terraform destroy` → verify via `resource_ids` nothing remains → record deployment) (contracts/pipeline-operations.md) — makes T054 pass
- [ ] T056 [US5] Create `.github/workflows/teardown.yml` (manual dispatch requiring typed `{client}-{env}` confirmation)
- [ ] T057 [US5] Confirm the update path (bump `app_version` → deploy STG → promote PROD) works end-to-end with health-gated rollout — makes T053 pass

**Checkpoint**: Full create → update → destroy lifecycle is operable.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Hardening and documentation across all stories

- [ ] T058 [P] Add a secret-leak scan to CI (`tests/contract/test_no_secret_leak.sh`) over repo + plan/log output (SC-010)
- [ ] T059 [P] Write `infra/terraform/README.md` documenting the provider-abstraction pattern, contracts, and how to add a new cloud
- [ ] T060 [P] Add `terraform fmt -check`, `validate`, and `tflint` as required CI checks that block merge (Principle VII, FR-006)
- [ ] T061 Run the quickstart.md end-to-end on the reference cloud and record results (deploy → switch cloud → promote → update → teardown)
- [ ] T062 [P] Verify all acceptance criteria in spec.md Success Criteria (SC-001..SC-012) are exercised by an automated test and map them in `tests/README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational — the MVP
- **US2 (Phase 4)**: Depends on US1 (extends the AWS module + adds pipeline)
- **US3 (Phase 5)**: Depends on Foundational; reuses the contract validated by US1. Can start once US1's module-interface tests pass; GCP/Azure modules (T043/T044) are parallel to each other
- **US4 (Phase 6)**: Depends on Foundational + at least one working module (US1). Naming/isolation enforcement spans all modules built so far
- **US5 (Phase 7)**: Depends on at least one working module (US1)
- **Polish (Phase 8)**: Depends on all targeted stories

### User Story Dependencies

- US1 (P1) → foundation of everything (reference cloud)
- US2 (P1) → builds on US1 (same cloud, adds tiers/promotion)
- US3 (P2) → independent of US2; needs the US1-proven contract
- US4 (P3) → needs US1; strengthened by US3 (applies to all modules)
- US5 (P3) → needs US1; teardown/verify applies per module

### Within Each User Story

- Tests written and FAILING before implementation (TDD, Principle II)
- common/contract before modules; modules before scripts; scripts before workflows

### Parallel Opportunities

- Setup: T003, T004, T005 in parallel
- Foundational: T009–T013 in parallel after T006–T008
- US1 tests: T014–T018 in parallel; US1 module files T019–T022 largely parallel (different files)
- US3: GCP (T043) and Azure (T044) modules in parallel by different workers
- All test tasks marked [P] within a story run in parallel

---

## Parallel Example: User Story 1

```bash
# Write all US1 tests first (they must fail):
Task: "Contract test modules/aws interface in tests/contract/test_aws_module_interface.sh"
Task: "Contract test config schema in tests/contract/test_config_schema.sh"
Task: "Integration smoke test in tests/integration/test_deploy_health.sh"
Task: "Idempotency test in tests/integration/test_idempotent_apply.sh"

# Then build AWS module resource files in parallel (different files):
Task: "AWS network in infra/terraform/modules/aws/network.tf"
Task: "AWS database in infra/terraform/modules/aws/database.tf"
Task: "AWS storage in infra/terraform/modules/aws/storage.tf"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1: Setup → 2. Phase 2: Foundational → 3. Phase 3: US1
4. **STOP and VALIDATE**: deploy to AWS, confirm health + DB + idempotency
5. This is a demoable MVP (one cloud, one environment)

### Incremental Delivery

1. Setup + Foundational → contract layer ready
2. US1 → deploy to AWS (MVP)
3. US2 → STG/PROD + gated promotion + auto-rollback
4. US3 → GCP + Azure portability
5. US4 → multi-client isolation
6. US5 → update + teardown lifecycle
7. Polish → hardening, docs, full quickstart run

### Notes

- [P] = different files, no dependencies
- Reference cloud is AWS; GCP/Azure deliberately deferred to US3 to keep US1 a tight MVP
- Verify each test fails before implementing; commit after each task or logical group
- Stop at any checkpoint to validate a story independently
