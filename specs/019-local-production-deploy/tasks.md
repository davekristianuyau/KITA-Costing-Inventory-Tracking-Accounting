---

description: "Task list for 019 local production-replica deployment"
---

# Tasks: Local Production-Replica Deployment

**Input**: Design documents from `/specs/019-local-production-deploy/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: INCLUDED — Constitution II (TDD) is non-negotiable, and this feature's whole premise is that
verification must be trustworthy. Every verification check is written and **observed failing** before the
deploy path that satisfies it. A check that has never been red is not evidence.

**Organization**: grouped by user story so each is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: US1–US4, mapping to spec.md
- Exact file paths in every task

---

## ⚠️ Scope reconciliation (read before starting)

US3's first acceptance scenario ("workloads run under the intended compute model") is satisfied in
**Phase 2**, not Phase 5. The reason is a hard dependency, not convenience: the module's current
`awsvpc` + serverless configuration has **never been observed placing running containers** on the
emulator, whereas EC2 + `bridge` was verified working (R3). US1 cannot deliver a browsable system without
it, so the launch-type change is a blocking prerequisite.

US3 therefore covers what remains and is still independently testable: **capacity provisioning** (the
real-cloud-only part the emulator cannot validate), the **database port** correction, and the
**enumerated gaps**. T009 records the decision so the reconciliation is visible rather than silently
diverging from the spec.

---

## Phase 1: Setup

**Purpose**: harness skeleton and hygiene

- [ ] T001 Create harness structure `sim/local-deploy/{lib,terraform}/` with executable-bit-correct shell stubs per plan.md
- [ ] T002 [P] Add `.gitignore` entries for `sim/local-deploy/terraform/.terraform*`, `*.tfstate*`, and any generated credential file (Constitution III, FR-023)
- [ ] T003 [P] Add `sim/local-deploy/README.md` stub declaring this as the fourth local stack, completed by T041 (gaps) and T050 (stack guide) (FR-026)
- [ ] T004 [P] Ensure all new `.sh` files are committed with the executable bit and LF endings — spec 010 lost a CI day to a missing exec bit that Windows hid

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: everything US1 needs before a single container can run

**⚠️ CRITICAL**: no user story work begins until this phase completes

- [ ] T005 Resolve the open question in [contracts/release-set.md](./contracts/release-set.md): whether a single-client deployment needs both `edge` and `gateway`; record the decision and rationale in that contract
- [ ] T006 Declare the full 9-unit release set in `sim/local-deploy/terraform/main.tf` per [contracts/release-set.md](./contracts/release-set.md), with `frontend` as the only `public` unit
- [ ] T007 Override or reconcile `gateway_key` in `infra/terraform/modules/aws/main.tf` so the ALB fronts the frontend, not a unit named `gateway` (release-set contract rule 2)
- [ ] T008 Write the Terraform wrapper root `sim/local-deploy/terraform/main.tf`: aws provider `endpoints{}` → Floci with dummy credentials, calling `infra/terraform/modules/aws` with `emulated = false`. **No `resource` blocks of its own** (FR-002)
- [ ] T009 Change `infra/terraform/modules/aws/compute.tf`: `requires_compatibilities` FARGATE→EC2, `network_mode` awsvpc→bridge, `launch_type` FARGATE→EC2, remove the `network_configuration` block (awsvpc-only). Note in the file why, referencing US3 scenario 1
- [ ] T010 Fix `infra/terraform/modules/aws/database.tf`: replace the hardcoded `:5432` in the JDBC URL with the created instance's `port` attribute (R5, FR-006) — this also fixes a latent production bug
- [ ] T011 Review every dependent of the `emulated` variable across `infra/terraform/modules/aws/`; remove the flag if RDS was its only purpose, else document what still needs it (Constitution VI)
- [ ] T012 [P] Implement `sim/local-deploy/lib/preflight.sh`: container runtime reachable and socket mountable, required host ports free (naming what holds a taken port), build tooling present — fails before any resource is created (FR-018)
- [ ] T013 Implement Floci bring-up in `sim/local-deploy/lib/preflight.sh`: socket mounted, `-u root`, publishing the API port and the ALB listener port, **never 5100** (R4 — the registry sibling binds it)
- [ ] T014 [P] Establish the Floci-as-resolver convention in `sim/local-deploy/lib/discovery.sh` so every helper container resolves Floci names (R1 — the single most error-prone fact in this feature)

**Checkpoint**: infrastructure definitions correct and the emulator can be brought up reproducibly

---

## Phase 3: User Story 1 - Bring the whole system up and use it (Priority: P1) 🎯 MVP

**Goal**: one command deploys all nine units and prints a URL that serves a working, signed-in system

**Independent Test**: run on a clean machine, then sign in and create a record in the browser

### Tests for User Story 1 ⚠️ write first, observe failing

- [ ] T015 [P] [US1] Failing check in `sim/local-deploy/lib/verify.sh`: entry URL returns success **from the host**, through the ALB (FR-012)
- [ ] T016 [P] [US1] Failing check in `sim/local-deploy/lib/verify.sh`: every unit's `health_path` reports healthy within the timeout (FR-015)

### Implementation for User Story 1

- [ ] T017 [US1] Implement image build for all nine units in `sim/local-deploy/lib/images.sh`, reusing existing Compose build contexts — **build sequentially**, parallel image builds fail spuriously on this machine
- [ ] T018 [US1] Implement idempotent ECR repository creation, tagging and push in `sim/local-deploy/lib/images.sh` — no `docker login`; tolerate both "already exists" and post-restart "not found" (R4)
- [ ] T019 [US1] Implement the apply phase in `sim/local-deploy/deploy-local.sh` (init + apply against the wrapper root), failing with exit 3 per [contracts/deploy-command.md](./contracts/deploy-command.md)
- [ ] T020 [US1] Implement alias binding in `sim/local-deploy/lib/discovery.sh`: resolve each placed container's generated name, attach **both** `{unit}.{client}-{env}.internal` and the bare `{unit}` (R6, R7)
- [ ] T021 [US1] Implement the bounded health gate in `sim/local-deploy/lib/verify.sh`, making T016 pass
- [ ] T022 [US1] Ensure a fully privileged account exists before verification runs — a system that refuses everyone is indistinguishable from a broken one (FR-024)
- [ ] T023 [US1] Implement success output in `sim/local-deploy/lib/report.sh`: entry URL, sign-in credentials, one-line summary of what was verified (FR-019)
- [ ] T024 [US1] Wire `sim/local-deploy/deploy-local.sh` end to end (preflight → build → publish → apply → bind → verify → report) with the flags in [contracts/deploy-command.md](./contracts/deploy-command.md)
- [ ] T025 [US1] Create `.claude/skills/deploy-local/SKILL.md` as a thin wrapper that invokes the harness and interprets its exit code — no behaviour of its own
- [ ] T026 [US1] Run quickstart §2 manually: open the URL, sign in, create a record, read it back. **Verify on the running stack** — 017 and 018 both shipped bugs every unit test passed over

**Checkpoint**: US1 complete — the system is deployable and usable in a browser

---

## Phase 4: User Story 2 - Refuse to report success on a broken system (Priority: P2)

**Goal**: verification exercises real traffic and cannot report success on an unusable system

**Independent Test**: break one internal dependency, run the deploy, confirm it fails and names it

### Tests for User Story 2 ⚠️ write first, observe failing

- [ ] T027 [P] [US2] Failing check in `sim/local-deploy/lib/verify.sh`: a cross-service action succeeds (FR-011)
- [ ] T028 [P] [US2] Failing check in `sim/local-deploy/lib/verify.sh`: an action by an unpermitted caller is **refused** (FR-025, SC-010)
- [ ] T029 [P] [US2] Fault-injection harness in `sim/local-deploy/lib/verify.sh` (or a sibling test script) covering the four cases in quickstart §3

### Implementation for User Story 2

- [ ] T030 [US2] Implement the cross-service check, making T027 pass — choose an action that genuinely traverses services (a workflow action reaching operations), not one that a single service could satisfy alone
- [ ] T031 [US2] Implement the permission-refusal check, making T028 pass
- [ ] T032 [US2] Enforce the verification contract in `sim/local-deploy/lib/verify.sh`: a run of only reachability + health checks is a **contract violation, not a pass** ([contracts/deploy-command.md](./contracts/deploy-command.md))
- [ ] T033 [US2] Implement exit codes 0–4 exactly as specified, ensuring exit 0 is impossible when any check failed (SC-003)
- [ ] T034 [US2] Implement failure reporting in `sim/local-deploy/lib/report.sh`: name the failing check and unit, and print a **copy-pasteable log command with the generated container name already resolved** (FR-014, SC-011)
- [ ] T035 [US2] Leave the environment running on verification failure, and say so in the output (FR-014)
- [ ] T036 [US2] Run all four fault injections from quickstart §3 and confirm each **fails the run**. If any passes, the verification is theatre — fix before proceeding

**Checkpoint**: US2 complete — a green run now means something

---

## Phase 5: User Story 3 - Production parity for compute (Priority: P3)

**Goal**: the module remains genuinely deployable to a real cloud, with its unverifiable parts named

**Independent Test**: inspect that capacity is defined and the real-cloud plan is complete; confirm the gaps document lists what local success does not prove

- [ ] T037 [US3] Create `infra/terraform/modules/aws/capacity.tf`: launch template (ECS-optimized AMI, Graviton per the roadmap), Auto Scaling Group, and ECS capacity provider (FR-021)
- [ ] T038 [US3] Associate the capacity provider with the cluster in `infra/terraform/modules/aws/compute.tf` so tasks are placeable in a real cloud
- [ ] T039 [US3] Confirm the real-cloud path stays clean: `terraform fmt -check -recursive`, `validate`, and `tflint --recursive` all pass. **Verify on Linux, not just Windows** — exec bits and line endings differ, which is exactly how spec 010's infra gate went red
- [ ] T040 [US3] Verify the database connection uses the created instance's reported port end to end, on the running stack (R5)
- [ ] T041 [US3] Write the enumerated known-gaps section into `sim/local-deploy/README.md` from [data-model.md](./data-model.md#known-local-gap), leading with capacity provisioning as the **highest-risk** gap (FR-022, SC-007)
- [ ] T042 [US3] Assert the wrapper root declares no resources of its own — `grep -rn 'resource "aws_' sim/local-deploy/terraform/` must return nothing (FR-002, SC-005)

**Checkpoint**: US3 complete — local parity is honest about its own limits

---

## Phase 6: User Story 4 - Repeatable teardown and re-run (Priority: P4)

**Goal**: tear down completely and redeploy with zero manual cleanup

**Independent Test**: deploy → teardown → deploy again, no manual intervention, no leftovers

### Tests for User Story 4 ⚠️ write first, observe failing

- [ ] T043 [P] [US4] Failing check: after teardown, none of the deployment's resources remain **and unrelated containers on the machine survive** (FR-017)
- [ ] T044 [P] [US4] Failing check: a re-run after an interrupted apply converges without manual cleanup (FR-016)

### Implementation for User Story 4

- [ ] T045 [US4] Implement `sim/local-deploy/teardown.sh`: destroy, then remove the emulator, its spawned containers, networks and image tags — scoped so unrelated containers are never touched
- [ ] T046 [US4] Make the deploy path idempotent end to end, making T044 pass
- [ ] T047 [US4] Handle emulator-restart recovery: recreate ECR repositories rather than failing on `RepositoryNotFoundException` (R4)
- [ ] T048 [US4] Confirm a rebuilt image is served after re-run (quickstart §6)
- [ ] T049 [US4] Measure a warm re-run (`--skip-build`) against the 5-minute budget (SC-008); record the actual figure

**Checkpoint**: all four stories independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T050 [P] Complete `sim/local-deploy/README.md`: what each of the four local stacks is for and which to reach for (FR-026)
- [ ] T051 [P] Update `docs/architecture.md` and the root `README.md` to mention the local production-replica path
- [ ] T052 Verify no secret, database password or credential beyond the demo sign-in appears in command output (FR-023, Constitution III)
- [ ] T053 Update `CLAUDE.md` — local stacks, the compute-model change, and the corrected Floci facts
- [ ] T054 Run the full [quickstart.md](./quickstart.md) end to end and record results in it, as spec 017 did
- [ ] T055 Capture durable context to memory via the `kita-context-capture` skill

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — **blocks all user stories**
- **US1 (Phase 3)**: depends on Foundational — the MVP
- **US2 (Phase 4)**: depends on US1 (it verifies a deployed system)
- **US3 (Phase 5)**: depends on Foundational only — **can run in parallel with US1/US2**, since it touches the module and documentation rather than the deploy path
- **US4 (Phase 6)**: depends on US1 (needs something to tear down)
- **Polish (Phase 7)**: depends on all desired stories

### Within each story

- Tests written and **observed failing** before implementation (Constitution II)
- Emulator bring-up before anything that talks to it
- Images before apply; apply before alias binding; alias binding before cross-service verification
- Never mark a task complete on unit-level evidence alone — this feature is about what a running system does

### Parallel opportunities

- T002, T003, T004 (Setup)
- T012, T014 (Foundational — different files)
- T015, T016 (US1 tests)
- T027, T028, T029 (US2 tests)
- T043, T044 (US4 tests)
- T050, T051 (Polish)
- **US3 in parallel with US1/US2** — the only story-level parallelism, since it does not touch the deploy path

---

## Parallel Example: User Story 2

```bash
# Write all three failing checks together, then observe them fail:
Task: "Failing check: a cross-service action succeeds in sim/local-deploy/lib/verify.sh"
Task: "Failing check: an unpermitted caller is refused in sim/local-deploy/lib/verify.sh"
Task: "Fault-injection harness covering the four cases in quickstart §3"
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 Setup
2. Phase 2 Foundational — **critical, blocks everything**
3. Phase 3 US1
4. **STOP and VALIDATE**: sign in and use the system in a browser
5. That alone replaces the manual bring-up and is worth having

### Incremental delivery

1. Setup + Foundational → the emulator and module are correct
2. **US1** → a browsable system (MVP)
3. **US2** → the green result becomes trustworthy
4. **US3** → the module stays deployable to a real cloud, with named gaps
5. **US4** → it becomes a daily habit rather than an ordeal

### Risk notes

- **T009 is the highest-leverage task**: it makes containers actually run. If the current configuration turns out to place containers on the emulator after all, revisit the scope reconciliation above and move it back to US3.
- **T037/T038 cannot be validated locally.** The emulator places tasks without capacity; real AWS does not. Treat local success as no evidence here, and lean on `plan`/`validate` plus review.
- **T020 is the subtlest**: aliases are lost when a task is replaced. Prefer failing loudly over degrading silently.
