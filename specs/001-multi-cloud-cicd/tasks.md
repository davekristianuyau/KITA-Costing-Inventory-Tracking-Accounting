---

description: "Task list for Multi-Cloud CI/CD Infrastructure Scaffolding (multi-service)"
---

# Tasks: Multi-Cloud CI/CD Infrastructure Scaffolding

**Input**: Design documents from `/specs/001-multi-cloud-cicd/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED. Contract tests (module-interface parity, config-schema/Release-Set validation)
and post-deploy smoke tests (public gateway reachable, backend services private, aggregate health,
gateway→service round trip, idempotency) — written before the modules/pipeline they cover.

**Deploys**: the KITA **service set** (Release Set) — frontend (Nginx), Spring Cloud Gateway, and
backend services (e.g. operations-service) + shared PostgreSQL — per environment, behind one public
gateway. Reference cloud = **AWS**; GCP/Azure added in US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency)

---

## Phase 1: Setup

- [X] T001 Create `infra/terraform/{modules/{common,aws,gcp,azure},backends,environments}`, `scripts/`, `tests/{contract,integration,fixtures}`, `.github/workflows/`
- [X] T002 `infra/terraform/versions.tf` pinning Terraform >= 1.9 and providers aws ~>5, google ~>5, azurerm ~>4
- [ ] T003 [P] `.tflint.hcl` + `terraform fmt`/`validate` config
- [ ] T004 [P] `tests/run.sh` runner for contract/smoke bash assertions
- [X] T005 [P] `.gitignore` for `.terraform/`, `*.tfstate*`, secret tfvars, plan files

---

## Phase 2: Foundational (Blocking)

- [X] T006 `infra/terraform/variables.tf` — the config contract incl. `release_set` map (service → image/version/visibility/port/health_path), with validation (enums, client_name regex, immutable versions, ≥1 public) per contracts/config-schema.md
- [X] T007 `infra/terraform/main.tf` — select per-cloud module by `var.cloud_provider`, passing the module-interface inputs
- [X] T008 `infra/terraform/outputs.tf` — `gateway_url`, `aggregate_health_url`, `service_endpoints`, `db_connection_secret_ref`, `object_storage_ref`, `environment_name`, `resource_ids`
- [X] T009 [P] `modules/common/` — naming `{client}-{env}[-service]`, mandatory tags, release-set validation locals (FR-020/021/022)
- [X] T010 [P] `scripts/validate-config.sh` — enforce config-schema incl. Release-Set rules; fail-fast (FR-006/007/013)
- [X] T011 [P] `backends/{aws,gcp,azure}.tfbackend` — remote state keyed `{client}-{env}`, locking
- [ ] T012 [P] `scripts/record-deployment.sh` — append audit incl. Release Set (FR-016)

**Checkpoint**: contract + config layer ready.

---

## Phase 3: User Story 1 & 6 - Deploy the multi-service set to a chosen cloud (Priority: P1) 🎯 MVP

**Goal**: one pipeline run provisions the full service set on AWS behind one public gateway, backend
services private, aggregate health green.

**Independent Test**: `deploy.sh --client <t> --env stg` on AWS → gateway URL healthy; a backend
service is NOT publicly reachable; gateway→service round trip works; re-run = no changes.

### Tests (write first, must FAIL) ⚠️
- [ ] T013 [P] [US1] `tests/contract/test_aws_module_interface.sh` — aws module declares the module-interface inputs/outputs
- [ ] T014 [P] [US1] `tests/contract/test_config_schema.sh` — validate-config accepts a valid Release Set, rejects floating tags / no-public / bad provider
- [ ] T015 [P] [US1] `tests/integration/test_deploy_health.sh` — after deploy, gateway aggregate health UP; gateway→operations-service round trip
- [ ] T016 [P] [US1] `tests/integration/test_backend_private.sh` — backend service has no public endpoint (SC-013)
- [ ] T017 [P] [US1] `tests/integration/test_idempotent_apply.sh` — second apply = zero changes (SC-003)
- [X] T018 [P] [US1] `tests/fixtures/aws-stg.tfvars` — sample Release Set for a throwaway client

### Implementation
- [X] T019 [US1] `modules/aws/network.tf` — VPC, public+private subnets, security groups (FR-001/008)
- [X] T020 [US1] `modules/aws/database.tf` — RDS PostgreSQL (private, encrypted, creds→Secrets Manager) (FR-013/014)
- [X] T021 [P] [US1] `modules/aws/storage.tf` — S3 (encrypted)
- [X] T022 [US1] `modules/aws/compute.tf` — one ECS Fargate service **per Release-Set entry** (`for_each`), task defs from image/version, env incl. service URLs + DB secret; Cloud Map for private discovery
- [X] T023 [US1] `modules/aws/ingress.tf` — public ALB + ACM cert + custom domain routing to `public` services only; backend services private (FR-001c/001b/004a)
- [X] T024 [US1] `modules/aws/health.tf` + module `variables.tf`/`outputs.tf` — aggregate health target; outputs matching the contract (makes T013 pass)
- [X] T025 [US1] Wire `modules/aws` into root `main.tf`; ensure `validate-config.sh` runs before plan
- [X] T026 [US1] `scripts/deploy.sh` — init (`{client}-{env}` state) → validate → plan → apply → wait aggregate health → smoke test → record deployment
- [X] T027 [US1] Ship per-service structured logs to CloudWatch; scrub secrets from output (FR-018/013)

**Checkpoint**: full multi-service stack deploys to AWS from one command. **MVP.**

---

## Phase 4: User Story 2 - STG/PROD with gated Release-Set promotion (Priority: P1)

**Goal**: two isolated tiers; promote the same validated Release Set STG→PROD; auto-rollback on
failed aggregate health.

### Tests (write first, must FAIL) ⚠️
- [ ] T028 [P] [US2] `test_stg_prod_isolation.sh` — STG/PROD share no network/DB/storage/services (SC-005)
- [ ] T029 [P] [US2] `test_promotion_gate.sh` — promote refuses a Release Set not healthy in STG (FR-011, SC-006)
- [ ] T030 [P] [US2] `test_no_auto_promote.sh` — STG deploy/merge never changes PROD (FR-009, SC-007)
- [ ] T031 [P] [US2] `test_auto_rollback.sh` — failed aggregate health keeps previous Release Set serving (FR-006a, SC-014)

### Implementation
- [ ] T032 [US2] Per-env sizing (STG small / PROD standard) in `modules/aws` without architecture change (FR-008a)
- [ ] T033 [US2] PROD DB daily backups + PITR gated on `env==prod` (FR-014a)
- [ ] T034 [US2] Health-gated per-service rollout + aggregate-health auto-rollback to previous Release Set (FR-006a) — makes T031 pass
- [ ] T035 [US2] `scripts/promote.sh` — enforce STG-healthy gate for the exact Release Set, apply PROD with same versions (no rebuild) — makes T029 pass
- [ ] T036 [P] [US2] `.github/workflows/deploy-stg.yml` (auto on merge → deploy.sh stg → smoke)
- [ ] T037 [P] [US2] `.github/workflows/promote-prod.yml` (manual dispatch, protected Environment approval → promote.sh) (FR-010)
- [ ] T038 [US2] Per-env, per-service secret scoping (STG vs PROD distinct) (FR-013)

**Checkpoint**: gated multi-service promotion + auto-rollback on AWS.

---

## Phase 5: User Story 3 - Portability across clouds (Priority: P2)

**Goal**: deploy the same service set to GCP and Azure by config only; reject unsupported providers.

### Tests (write first, must FAIL) ⚠️
- [ ] T039 [P] [US3] `test_gcp_module_interface.sh`
- [ ] T040 [P] [US3] `test_azure_module_interface.sh`
- [ ] T041 [P] [US3] `test_provider_switch.sh` — only config changes to switch providers (FR-003, SC-002)
- [ ] T042 [P] [US3] `test_invalid_provider.sh` — unsupported provider rejected before provisioning (FR-007)
- [ ] T043 [P] [US3] `tests/fixtures/{gcp,azure}-stg.tfvars`

### Implementation
- [ ] T044 [P] [US3] `modules/gcp/` — Cloud Run per service (+ Serverless VPC connector, internal ingress for backend), Cloud SQL (private IP), GCS, Secret Manager, managed cert — to the module-interface contract
- [ ] T045 [P] [US3] `modules/azure/` — Container Apps env (external/internal ingress per visibility), Azure DB for PostgreSQL Flexible (private), Blob, Key Vault, managed cert — to the contract
- [ ] T046 [US3] Extend root `main.tf` to route gcp/azure; verify identical output surface across modules
- [ ] T047 [US3] Add GCP/Azure remote-state backends to `deploy.sh`/`promote.sh`
- [ ] T048 [US3] Verify single-region/in-region-data policy across all three modules (FR-002a)

**Checkpoint**: any of the three clouds selectable by config.

---

## Phase 6: User Story 4 - Isolated per-client environments (Priority: P3)

### Tests (write first, must FAIL) ⚠️
- [ ] T049 [P] [US4] `test_client_isolation.sh` — two clients share no network/DB/storage/services across tiers (SC-008)
- [ ] T050 [P] [US4] `test_naming_convention.sh` — 100% of resources follow `{client}-{env}[-service]` + tags (FR-020/021, SC-012)

### Implementation
- [ ] T051 [US4] `environments/<client>/{stg,prod}.tfvars` convention + example client; per-client state keys; enforce naming/tags in `modules/common` + all modules
- [ ] T052 [P] [US4] `scripts/onboard-client.sh` — scaffold a client's tfvars (with a starter Release Set) + validate

**Checkpoint**: multiple isolated client deployments coexist.

---

## Phase 7: User Story 5 - Lifecycle management (Priority: P3)

### Tests (write first, must FAIL) ⚠️
- [ ] T053 [P] [US5] `test_release_set_update.sh` — new Release Set becomes active + healthy (FR-015/022)
- [ ] T054 [P] [US5] `test_teardown_complete.sh` — teardown removes 100% of an env's resources; other tier/clients intact (SC-009/008)

### Implementation
- [ ] T055 [US5] `scripts/teardown.sh` — destroy target env, verify via `resource_ids` — makes T054 pass
- [ ] T056 [US5] `.github/workflows/teardown.yml` (manual, typed `{client}-{env}` confirmation)
- [ ] T057 [US5] Confirm update path (bump Release Set → deploy STG → promote PROD) end-to-end — makes T053 pass

**Checkpoint**: create → update → destroy lifecycle works.

---

## Phase 8: Polish

- [ ] T058 [P] Secret-leak scan in CI over repo + plan/log output (SC-010)
- [X] T059 [P] `infra/terraform/README.md` — provider-abstraction + Release-Set model + how to add a cloud/service
- [ ] T060 [P] Add `terraform fmt -check`, `validate`, `tflint` as blocking CI checks (Principle VII, FR-006)
- [ ] T061 Run quickstart end-to-end on AWS (deploy → switch cloud → promote → update → teardown); record results
- [ ] T062 [P] Map SC-001..SC-014 to covering tests in `tests/README.md`

---

## Dependencies & Execution Order
- Setup → Foundational → US1/US6 (AWS multi-service MVP) → US2 (promotion) → US3 (gcp/azure) →
  US4 (isolation) → US5 (lifecycle) → Polish.
- US3 GCP (T044) ∥ Azure (T045). Tests precede implementation within each story (TDD).

## Implementation Strategy
MVP = Setup + Foundational + US1/US6 (multi-service set on AWS, gateway public, backends private,
aggregate health). Then promotion, portability, isolation, lifecycle, polish.

## Notes
- Highest-risk tasks: private-vs-public exposure (T016/T023), aggregate-health auto-rollback (T034),
  Release-Set gate (T035), isolation (T028/T049) — keep those tests rigorous.
- [P] = different files, no dependencies. Commit after each task/group and push per project workflow.
