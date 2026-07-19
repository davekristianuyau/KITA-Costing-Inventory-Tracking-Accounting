---
description: "Task list for 010-floci-multicloud-ci"
---

# Tasks: Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

**Input**: Design documents from `/specs/010-floci-multicloud-ci/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the deploy checks themselves ARE the tests (apply→verify→destroy), and the constitution
mandates a regression must fail (SC-005). "Test" tasks here are the run/assert steps of the checks.

**Organization**: By user story. Harness lives in `sim/cloud-deploy/`; the 001 modules are edited **additively**
(a default-off `emulated` flag + missing version/provider constraints). US1 (green gate) is independent of the
deploy harness.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Create `sim/cloud-deploy/` + `sim/cloud-deploy/docker-compose.yml` — services `floci` (floci/floci:latest, :4566), `floci-gcp` (floci-io/floci-gcp, :4588), `floci-az` (floci-io/floci-az, :4577), a one-shot `terraform` runner (`hashicorp/terraform:1.9.8`, mounts repo `../../:/work`), and an `awscli` tool (`amazon/aws-cli:latest`); add `.gitignore` entries for `sim/cloud-deploy/**/.terraform/`, `terraform.tfstate*`, `.terraform.lock.hcl`

---

## Phase 2: Foundational (Blocking Prerequisites for US2–US4)

**⚠️ Blocks the deploy stories (not US1).**

- [X] T002 `sim/cloud-deploy/probe.sh <cloud>` — generic coverage probe (contracts/probe.md): start that cloud's Floci emulator, minimally `terraform apply` each 001-module resource type in isolation against it (dummy creds), record `supported`/`unsupported` → `sim/cloud-deploy/coverage/<cloud>.md`, then destroy + tear down (FR-011)
- [X] T003 `sim/cloud-deploy/deploy-check.sh <cloud>` — generic check (contracts/deploy-check.md): start emulator → `terraform init/apply` the wrapper root `sim/cloud-deploy/<cloud>/` → `terraform state list` verify → `terraform destroy` → assert no residue; clear per-phase pass/fail; bounded readiness wait (no hang)
- [X] T004 [P] `sim/cloud-deploy/run-all.sh` — run `deploy-check.sh` for aws, gcp, azure; report per-cloud + overall pass/fail; guarantee teardown of all emulators (SC-004/SC-006)

---

## Phase 3: User Story 1 - Green infra gate (Priority: P1) 🎯 MVP

**Goal**: The CI `infra` check passes (fmt/validate/tflint) on `main` and every branch, without changing any
deploy behavior — a red check becomes trustworthy again.

**Independent Test**: Run the gate (`terraform fmt -check -recursive` + `validate` + `tflint --recursive` +
`tests/run.sh contract`) → all pass; introduce a real error → it fails and names the file. (No dependency on the
deploy harness.)

- [X] T005 [P] [US1] `infra/terraform/modules/aws/versions.tf` — add `terraform { required_version = ">= 1.9" required_providers { aws = {...} random = {...} } }` (additive; tflint `terraform_required_providers`/`_version`)
- [X] T006 [P] [US1] `infra/terraform/modules/gcp/versions.tf` — add `required_version` + `required_providers { google = {...} random = {...} }`
- [X] T007 [P] [US1] `infra/terraform/modules/azure/versions.tf` — add `required_version` + `required_providers { azurerm = {...} random = {...} }`
- [X] T008 [P] [US1] `infra/terraform/modules/common/versions.tf` — add `required_version` (no providers; naming-only module)
- [X] T009 [US1] Run `terraform fmt -recursive infra/terraform`; run the full gate locally (fmt-check + `validate` + `tflint --recursive` + `bash tests/run.sh contract`) → **all green** (SC-001)
- [X] T010 [US1] Regression check: introduce a temporary invalid attribute in an `infra/terraform` file → gate **fails** naming the file → revert (US1 AC-2)

**Checkpoint**: `main`'s infra gate is green; the quality gate still catches real errors.

---

## Phase 4: User Story 2 - AWS deployment proven locally (Priority: P2)

**Goal**: The real 001 AWS module `terraform apply`s against local Floci (apply→verify→destroy), no real cloud.

**Independent Test**: `bash sim/cloud-deploy/deploy-check.sh aws` → pass (resources in `state list`, then
destroyed, dummy creds only).

- [X] T011 [US2] Run `bash sim/cloud-deploy/probe.sh aws`; commit the resulting `sim/cloud-deploy/coverage/aws.md` (measured AWS coverage — FR-011)
- [X] T012 [US2] Add `variable "emulated"` (bool, default false) to `infra/terraform/modules/aws` and guard any resources `coverage/aws.md` marks `unsupported` with `count`/`for_each` (contracts/module-emulated-flag.md); default off ⇒ real-cloud unchanged
- [X] T013 [US2] Create `sim/cloud-deploy/aws/main.tf` — provider `aws` with `endpoints`→`http://floci:4566` + dummy creds + skip flags, calling `module "aws" { source = "../../../infra/terraform/modules/aws" emulated = true ... }` (supply `client_name`, `env`, `region`, `release_set`, `size`, `custom_domain=""`, `db_backup_retention_days`, `tags`)
- [X] T014 [US2] Run `bash sim/cloud-deploy/deploy-check.sh aws` → apply/verify/destroy **pass**; confirm only dummy creds + the local endpoint were used (SC-002/SC-003)
- [X] T015 [P] [US2] Real-cloud invariant: `terraform plan` of `modules/aws` with `emulated = false` is unchanged vs. the pre-feature baseline (FR-005)

**Checkpoint**: the real AWS module deploys locally, apply→destroy clean, zero real cloud.

---

## Phase 5: User Story 3 - GCP and Azure deployments proven locally (Priority: P3)

**Goal**: Same apply→verify→destroy for the GCP and Azure 001 modules against their Floci emulators.

**Independent Test**: `deploy-check.sh gcp` and `deploy-check.sh azure` each pass for the emulator-supported set.

- [ ] T016 [P] [US3] Run `bash sim/cloud-deploy/probe.sh gcp`; commit `sim/cloud-deploy/coverage/gcp.md`
- [ ] T017 [P] [US3] Run `bash sim/cloud-deploy/probe.sh azure`; commit `sim/cloud-deploy/coverage/azure.md`
- [ ] T018 [US3] Add `emulated` flag to `infra/terraform/modules/gcp`, guarding the `unsupported` set from `coverage/gcp.md` (expected: `google_compute_*`, `google_sql_*`, `google_vpc_access_connector`, `google_service_networking_connection`)
- [ ] T019 [US3] Add `emulated` flag to `infra/terraform/modules/azure`, guarding the `unsupported` set from `coverage/azure.md` (expected: `azurerm_container_app*`, `azurerm_postgresql_flexible_*`, `azurerm_virtual_network`/`azurerm_subnet`, `azurerm_log_analytics_workspace`, `azurerm_private_dns_*`)
- [ ] T020 [P] [US3] Create `sim/cloud-deploy/gcp/main.tf` — provider `google` with endpoint→`http://floci-gcp:4588` + dummy creds, calling `module "gcp" { source = "../../../infra/terraform/modules/gcp" emulated = true ... }`
- [ ] T021 [P] [US3] Create `sim/cloud-deploy/azure/main.tf` — provider `azurerm` pointed at `http://floci-az:4577` + dummy creds, calling `module "azure" { source = "../../../infra/terraform/modules/azure" emulated = true ... }`
- [ ] T022 [US3] Run `bash sim/cloud-deploy/deploy-check.sh gcp` → **pass** (emulator-supported set applies + destroys; dummy creds only)
- [ ] T023 [US3] Run `bash sim/cloud-deploy/deploy-check.sh azure` → **pass**
- [ ] T024 [P] [US3] Real-cloud invariant: `emulated = false` plan of `modules/gcp` and `modules/azure` unchanged vs. baseline (FR-005)

**Checkpoint**: all three clouds' modules deploy locally to the measured emulator boundary.

---

## Phase 6: User Story 4 - One-command local run + CI wiring (Priority: P3)

**Goal**: One command runs all three clouds locally; CI runs it (fast gate blocking, heavy apply non-blocking).

**Independent Test**: `bash sim/cloud-deploy/run-all.sh` → overall pass < 15 min; a CI run shows `infra` blocking
+ `cloud-deploy` as a separate non-blocking check.

- [ ] T025 [US4] Finalize `sim/cloud-deploy/run-all.sh` — all three deploy-checks pass, completes < 15 min, leaves 0 residue (SC-004/SC-006)
- [ ] T026 [US4] `.github/workflows/ci.yml` — confirm `infra` gate green + Terraform pinned `1.9.8` (blocking); add a **non-blocking** `cloud-deploy` job (`continue-on-error: true`) running `bash sim/cloud-deploy/run-all.sh` (contracts/ci-jobs.md)
- [ ] T027 [US4] Regression test (SC-005): a breaking change in one cloud's module turns `deploy-check`/the `cloud-deploy` job **red** while validate-only `infra` may still pass → revert

**Checkpoint**: the multi-cloud deploy check is one command locally and continuously enforced in CI.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T028 [P] `sim/cloud-deploy/README.md` — the per-cloud coverage boundary (summarize `coverage/*.md`), the `emulated` flag, and usage (probe / deploy-check / run-all)
- [ ] T029 [P] Update root `README.md` + `infra/terraform/README.md` with the local multi-cloud Terraform-deploy workflow (link quickstart.md)
- [ ] T030 [P] Security review: grep confirms only dummy creds + local emulator endpoints, no real cloud creds, and all Terraform state is gitignored (SC-003)
- [ ] T031 Run `quickstart.md` end-to-end: gate green (US1) → probe + deploy-check all three (US2/US3) → `run-all.sh` pass (US4)

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T004)** → US2/US3/US4.
- **US1 (T005–T010)**: **independent** — no dependency on the harness; do it first (fixes the red `main` gate). MVP.
- **US2 (T011–T015)**: after Foundational. Probe (T011) → emulated flag (T012) → wrapper root (T013) → check (T014).
- **US3 (T016–T024)**: after Foundational; best after US2 (pattern established). Probe → flag → wrapper → check per cloud.
- **US4 (T025–T027)**: after US2 + US3.
- **Polish (T028–T031)**: after the desired stories.

### Within a story

- Probe (measure coverage) **before** adding the `emulated` guards **before** the wrapper root **before** the check.
- The real-cloud invariant task ([P]) can run once the flag exists.

### Parallel Opportunities

- US1: T005/T006/T007/T008 (four different `versions.tf` files) in parallel.
- US3: T016/T017 (probes) parallel; T020/T021 (wrapper roots) parallel.
- Invariant checks (T015, T024) parallel with their story's later tasks.

---

## Implementation Strategy

### MVP First (US1)

Setup is not needed for US1. Do **US1 → STOP & VALIDATE**: `main`'s infra gate is green and still catches real
errors — this alone removes the permanently-red check.

### Incremental Delivery

US1 (green gate) → Setup + Foundational → US2 (AWS deploys) → US3 (GCP + Azure deploy) → US4 (one command + CI).
Each is independently testable and adds value.

---

## Notes

- **No real cloud, ever** — dummy creds + local Floci only (FR-004/SC-003); Terraform state gitignored.
- The `emulated` flag is **additive, default off** — real-cloud `plan` output is unchanged (FR-005); guards are
  added **only** where the probe proves the emulator can't provision a resource.
- The full 3-cloud apply is heavy (three emulators) → its CI job is **non-blocking** (mirrors 009's `sim-smoke`).
- Commit after each task or logical group; simple messages, no AI attribution.
