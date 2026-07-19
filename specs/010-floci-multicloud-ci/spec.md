# Feature Specification: Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

**Feature Branch**: `010-floci-multicloud-ci`
**Created**: 2026-07-19
**Status**: Draft
**Input**: User description: "next spec 010 is to fix CI infra, we can now point it to the local floci AWS, GCP and Azure to deploy as is with terraform"

## Summary

The CI **infra gate is red** — its lint step fails on the multi-cloud Terraform (feature 001), so `main` and
every branch show a failing check even when nothing is actually wrong with a deployment. Worse, the gate only
ever **validated** the Terraform (format, syntax, lint); it never proved the modules can actually *stand up*.

Feature 009 proved that a **local cloud emulator (Floci)** can run the real Terraform for a client's AWS
deployment with no cloud spend. Floci also emulates **GCP and Azure** locally. This feature (1) **fixes the
failing infra checks** so CI is green again, and (2) upgrades the infra gate to actually **`terraform apply`
the 001 modules against local Floci emulators for all three clouds** — deploying "as is" with **zero real
cloud credentials or spend**. This turns "the Terraform is syntactically valid" into "the Terraform actually
deploys on AWS, GCP, and Azure," catching real regressions the current gate misses.

## Clarifications

### Session 2026-07-19

- Q: What does "point CI to local Floci" cover — validate only, or actually deploy? → A: **Actually deploy** —
  run `terraform apply` (then destroy) of the 001 modules against local Floci emulators, not just fmt/validate/
  lint. The lint/validate fixes make the gate green; the apply step makes it *meaningful*.
- Q: All three clouds? → A: **Yes — AWS, GCP, and Azure**, each against its Floci emulator, deploying the 001
  module for that cloud as-is with no real cloud.
- Q: Real cloud credentials or spend at any point? → A: **Never.** Everything runs against the local emulators
  with dummy credentials (as established for AWS in feature 009).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Green infra gate (Priority: P1)

As the developer, when I push a branch or merge to `main`, the CI **infra** check passes instead of showing a
red failure caused by lint issues in the 001 Terraform — so a red check reliably means something is actually
wrong, and the quality gate is trustworthy again.

**Why this priority**: The gate is currently red on `main` for a reason unrelated to any real defect (it
failed the 009 merge's checks). A permanently-red check trains everyone to ignore CI, defeating the gate. This
is the immediate, standalone fix and delivers value on its own.

**Independent Test**: Push a commit; the infra job's format + validate + lint steps all pass (exit 0) on both a
feature branch and `main`, with no change to how any module deploys.

**Acceptance Scenarios**:

1. **Given** the current 001 Terraform, **When** CI runs the infra format/validate/lint steps, **Then** they
   all pass and the infra check is green.
2. **Given** a developer introduces a genuine Terraform format or validity error, **When** CI runs, **Then**
   the infra check fails and names the offending file — i.e. the gate still catches real problems.

---

### User Story 2 - AWS deployment proven locally in CI (Priority: P2)

As the developer, I want CI (and my local machine) to **actually deploy the AWS module** against a local AWS
emulator — apply the resources, confirm they exist, then destroy them — using no real AWS account, so a change
that breaks the AWS deployment is caught before it ever reaches a real cloud.

**Why this priority**: This is the core upgrade from "validated" to "deploys." AWS is already proven feasible
(feature 009's `sim/aws-imitation`), so it's the lowest-risk cloud to make a first-class, repeatable check.

**Independent Test**: Run the AWS deploy check; a local AWS emulator starts, `terraform apply` of the 001 AWS
module succeeds against it, the expected resources are present, `terraform destroy` cleans up, and no real AWS
credentials were used — all reported as pass/fail.

**Acceptance Scenarios**:

1. **Given** a running local AWS emulator, **When** the AWS deploy check applies the 001 AWS module, **Then**
   the apply succeeds and the expected resources exist in the emulator.
2. **Given** a successful apply, **When** the check finishes, **Then** it destroys everything it created,
   leaving no residue.
3. **Given** the check runs, **When** it completes, **Then** it used only dummy credentials — no real AWS
   account, endpoint, or spend was involved.

---

### User Story 3 - GCP and Azure deployments proven locally (Priority: P3)

As the developer, I want the same local apply-then-destroy deploy check for the **GCP** and **Azure** modules
against their Floci emulators, so all three of the clouds a client can choose (feature 001) have their
deployment path exercised locally with no real cloud.

**Why this priority**: Completes the multi-cloud promise. It comes after AWS because the GCP/Azure emulators
cover a different (and likely smaller) set of services, so the achievable "deploy as is" boundary must be
mapped per cloud.

**Independent Test**: Run the GCP deploy check and the Azure deploy check; each starts its emulator, applies
its 001 module (to the extent the emulator supports), confirms the created resources, destroys them, and uses
no real credentials.

**Acceptance Scenarios**:

1. **Given** a running local GCP emulator, **When** the GCP deploy check applies the 001 GCP module, **Then**
   the emulator-supported resources are created and then destroyed, with no real GCP project.
2. **Given** a running local Azure emulator, **When** the Azure deploy check applies the 001 Azure module,
   **Then** the emulator-supported resources are created and then destroyed, with no real Azure subscription.
3. **Given** any cloud's emulator does not support a resource in its module, **When** the check runs, **Then**
   the boundary is handled deliberately (documented and, if needed, guarded) rather than failing opaquely.

---

### User Story 4 - One-command local run + CI wiring (Priority: P3)

As the developer, I want to run the whole "deploy all three clouds locally" check with a **single command** on
my machine, and have **CI run it automatically** — with the fast lint/validate gate blocking and the heavy
apply checks kept from blocking every push — so the check is both easy to reproduce locally and continuously
enforced without slowing the pipeline.

**Why this priority**: Ergonomics + continuous enforcement. Valuable but depends on US1–US3 existing first.

**Independent Test**: From a clean checkout, one command brings up the emulators and runs all three clouds'
apply/destroy checks to completion; and a CI run shows the lint/validate gate as blocking and the multi-cloud
apply as a separate job.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** the developer runs the single documented command, **Then** all three
   clouds' deploy checks run and report an overall pass/fail.
2. **Given** a push, **When** CI runs, **Then** the lint/validate infra gate must pass to merge, while the
   heavy multi-cloud apply runs as its own (non-blocking) job.

### Edge Cases

- A cloud emulator is unreachable or slow to start → the check waits within a bounded time then fails with a
  clear message (no indefinite hang).
- The emulator does not implement a resource type used by a module → the check reports it as a known,
  documented coverage gap rather than a mysterious apply error.
- An apply partially fails → the check still attempts a full destroy so no emulator state or containers leak.
- Two clouds' checks run at once → each cloud's Terraform state and emulator are isolated so they don't collide.
- Terraform version drift (the pinned CI version vs a newer local install) → the check pins one version so
  local and CI behave identically.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The CI infra gate MUST pass its format, validity, and lint steps on `main` and on every branch;
  the current lint failures in the 001 Terraform MUST be fixed **without changing how any module deploys**.
- **FR-002**: The system MUST deploy each cloud's 001 Terraform module (AWS, GCP, Azure) against a **local
  cloud emulator**, using the module **as-is** to the extent the emulator supports it.
- **FR-003**: Each cloud deploy check MUST **apply then destroy**, verify the created resources exist between
  those steps, and report a clear pass/fail — leaving no residual resources, containers, or state.
- **FR-004**: No **real** cloud credentials, accounts, projects, subscriptions, endpoints, or spend may be used
  at any point; only local emulators with dummy credentials.
- **FR-005**: Any accommodation required for an emulator's limits MUST NOT alter the module's real-cloud
  behavior or output (a change that only affects the emulated run is acceptable; one that changes a real
  deployment is not).
- **FR-006**: CI MUST run these checks: the fast format/validate/lint gate is **blocking**; the heavy
  multi-cloud apply MUST run as a **separate job** that does not block routine merges (mirroring how the
  feature-009 full-sim smoke is wired).
- **FR-007**: A developer MUST be able to run the full multi-cloud deploy check **locally with a single
  command**, with no host cloud SDK/CLI/Terrafom install strictly required beyond what the project already
  ships (containerized runners are acceptable, as in feature 009).
- **FR-008**: Each cloud's Terraform state and emulator MUST be isolated so concurrent or repeated runs do not
  collide or leak across clouds.
- **FR-009**: The per-cloud **emulator coverage** (which module resources actually deploy vs. which are
  represented/skipped) MUST be documented so the boundary of "deploy as is" is explicit.
- **FR-010**: The deploy checks MUST pin a single Terraform version so local and CI runs are identical.

### Key Entities *(include if feature involves data)*

- **Infra CI gate**: the automated quality check for `infra/terraform` — today it formats/validates/lints; this
  feature makes it green and adds a deploy dimension.
- **Cloud emulator**: a local stand-in for a real cloud provider (Floci: AWS, GCP, Azure), reachable with dummy
  credentials, that Terraform targets instead of the real cloud.
- **Cloud Terraform module**: the existing feature-001 per-cloud module (AWS/GCP/Azure) that provisions a
  client's deployment (network, storage, secrets, managed compute/DB).
- **Deploy check run**: one apply → verify → destroy cycle for one cloud against its emulator, with an outcome.
- **Release Set**: the existing version-consistent set of service images a deployment provisions (unchanged;
  referenced by the modules).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The infra CI check passes on `main` — **0** failing format/validate/lint items — and still fails
  when a genuine Terraform error is introduced.
- **SC-002**: **All 3** clouds' 001 modules `terraform apply` successfully against their local emulators and
  `terraform destroy` cleanly, both in CI and from the single local command.
- **SC-003**: **0** real cloud credentials, accounts, or spend are used — verifiable from the run (only dummy
  credentials and local emulator endpoints appear).
- **SC-004**: A developer can run the full multi-cloud deploy check from a clean checkout with **one command**,
  and it completes (all three clouds) in a bounded, documented time.
- **SC-005**: A change that breaks any cloud's Terraform deployment is **caught by the deploy check** (it fails)
  rather than passing a validate-only gate — demonstrated by an intentional breaking change.
- **SC-006**: After every check run, **0** leftover emulator resources, containers, or Terraform state remain.

## Assumptions

- **Floci** is the local cloud emulator for all three providers (AWS on 4566, Azure on 4577, GCP on 4588),
  extending the AWS approach already proven in feature 009 (`sim/aws-imitation/`).
- **"Deploy as is"** means applying the unmodified 001 modules against the emulators; where an emulator does
  not implement a resource, that is the documented coverage boundary. Minimal, emulator-only accommodations
  (e.g., an existing variable to skip a resource, or a targeted apply) are acceptable **only** if they leave
  real-cloud behavior unchanged (FR-005). The exact per-cloud supported set is resolved during planning/research.
- The 001 modules provision managed compute/DB (e.g., container runtime + managed PostgreSQL) plus networking,
  storage, and secrets; emulator coverage varies by cloud, so the achievable apply differs per provider and is
  mapped in research.
- Checks run via **containers** (emulator + a pinned Terraform runner), so no real cloud SDK/CLI is required —
  consistent with feature 009. A single Terraform version is pinned; the newer version installed locally
  (`C:\Terraform`, 1.15.8) is reconciled against the CI pin rather than both being used ad hoc.
- The current infra lint failures are the specific, fixable warnings surfaced by the linter on the 001 azure/
  common/gcp modules (missing version/provider constraints); fixing them is additive metadata, not a behavior
  change.

## Dependencies

- **Feature 009** — the Floci-based local deploy pattern, containerized Terraform runner, and the non-blocking
  heavy-CI-job precedent (`sim/sim-smoke`) this feature reuses and generalizes to three clouds.
- **Feature 001** — the multi-cloud Terraform (`infra/terraform`, AWS/GCP/Azure modules) that is fixed and
  deployed here; this feature does not change what 001 deploys to real clouds.
- **Existing CI** (`.github/workflows/ci.yml`) — the infra job that is repaired and extended.

## Out of Scope

- Deploying to **real** AWS/GCP/Azure (that remains feature 001's concern; this feature is local-only).
- Full runtime fidelity of managed services beyond what the emulators provide (the emulators prove the
  Terraform/control-plane path, not production traffic behavior).
- Application/runtime behavior of the deployed services (covered by features 003–009 and their own tests).
- Adding new cloud providers beyond AWS/GCP/Azure.
