# Implementation Plan: Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

**Branch**: `010-floci-multicloud-ci` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-floci-multicloud-ci/spec.md`

## Summary

The CI **infra gate** is red (tflint flags missing version/provider constraints in the 001 modules) and only
ever *validates* Terraform — it never proves the modules deploy. This feature (1) **fixes** the gate with
additive metadata so it goes green, and (2) upgrades it to actually **`terraform apply` the real 001 modules
against local Floci emulators** for AWS, GCP, and Azure — apply → verify → destroy, with **zero real cloud
credentials or spend**. Per-cloud emulator coverage is **measured empirically first** (a probe), and resources
an emulator can't provision are skipped via an additive, default-off `emulated` module flag (real-cloud
behavior unchanged). A fast fmt/validate/tflint gate stays blocking; the heavy 3-cloud apply runs as a separate
non-blocking CI job. See [research.md](./research.md) for the decisions.

## Technical Context

**Language/Version**: Terraform HCL, pinned to **1.9.8** via the `hashicorp/terraform:1.9.8` container; Bash
(harness scripts); YAML (GitHub Actions). No application code.
**Primary Dependencies**: **Floci** local cloud emulators — AWS `floci/floci:latest` (:4566), GCP
`floci-io/floci-gcp` (:4588), Azure `floci-io/floci-az` (:4577); the existing **feature-001** Terraform
(`infra/terraform`, modules aws/gcp/azure/common); `hashicorp/terraform:1.9.8` runner container; Docker
Compose v2. Reuses feature-009's containerized-runner pattern.
**Storage**: none — ephemeral Terraform state per run (gitignored); emulator state is in-memory/disposable.
**Testing**: the deploy checks ARE the tests (apply → `state list` → destroy); plus the blocking
fmt/validate/tflint/contract gate. All host-tool-free (containerized).
**Target Platform**: Linux containers — CI (`ubuntu-latest`) and local Docker Desktop.
**Project Type**: Infrastructure / CI tooling (no app tier).
**Performance Goals**: full 3-cloud local deploy check completes in **< 15 min** (SC-004); the fast gate in
seconds.
**Constraints**: **0** real cloud credentials/accounts/spend (FR-004/SC-003); no host `terraform`/cloud-CLI
required beyond Docker (FR-007); single pinned Terraform version (FR-010); every run leaves **0** residue
(SC-006).
**Scale/Scope**: 3 clouds × (probe + one module apply/destroy) + the gate fix. ~1 new `sim/cloud-deploy/`
harness, additive edits to the 4 flagged 001 modules, and CI job additions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec + clarification complete; plan precedes code. |
| II. Test-Driven Development | ✅ Red-first: the probe + deploy checks fail on a broken module; an **intentional breaking change MUST fail** the check (SC-005). The gate fails on a real fmt/validity error (US1 AC-2). |
| III. Security & Data Integrity | ✅ **No real cloud creds/spend anywhere** — dummy creds + local emulators only; no secrets committed; the `emulated` flag never alters real-cloud output. |
| IV. Environment Isolation | ✅ Each cloud's emulator + Terraform state isolated; ephemeral; full teardown (destroy) after each run. |
| V. Observability & Debuggability | ✅ Each check reports clear pass/fail + the resources applied; bounded emulator-readiness waits (no hangs). |
| VI. Simplicity & YAGNI | ⚠️ Adds three emulators + a compat flag to 001 modules. Justified: it validates the **real** 001 deploy path locally (the gate's whole purpose). Kept minimal — reuse 009's pattern, Terraform-native verification, guards only where the probe proves they're needed. See Complexity Tracking. |
| VII. Automated Quality Gates | ✅ **This feature is a quality-gate improvement** — fixes the red gate and makes it actually deploy. |

**Result**: PASS with one tracked complexity item.

## Project Structure

### Documentation (this feature)

```text
specs/010-floci-multicloud-ci/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── deploy-check.md      # deploy-check.sh <cloud> contract (apply→verify→destroy)
│   ├── probe.md             # probe.sh <cloud> coverage-map contract (FR-011)
│   ├── module-emulated-flag.md  # the `emulated` variable semantics per 001 module
│   └── ci-jobs.md           # blocking gate + non-blocking cloud-deploy job
└── checklists/requirements.md
```

### Source Code (repository root)

```text
sim/
└── cloud-deploy/                 # NEW — deploy the REAL 001 modules against local Floci, per cloud
    ├── docker-compose.yml        # floci (aws:4566) + floci-gcp:4588 + floci-az:4577 + terraform runner
    ├── aws/main.tf               # provider→floci + module { source = ../../../infra/terraform/modules/aws, emulated=true }
    ├── gcp/main.tf               # provider→floci-gcp + module { source = .../modules/gcp, emulated=true }
    ├── azure/main.tf             # provider→floci-az + module { source = .../modules/azure, emulated=true }
    ├── probe.sh                  # FR-011 coverage probe (task 1) → coverage map per cloud
    ├── deploy-check.sh <cloud>   # start emulator → tf init/apply → state list → destroy
    └── run-all.sh                # one-command: all three clouds (SC-004)

infra/terraform/modules/          # feature 001 — EDITED additively:
├── aws/, gcp/, azure/            #   + `variable "emulated"` (default false) guarding unsupported resources
├── {aws,gcp,azure}/versions.tf   #   + required_version + required_providers (tflint fix, D6)
└── common/                       #   + required_version (tflint fix)

.github/workflows/ci.yml          # EDITED — infra gate green (blocking) + new non-blocking `cloud-deploy` job
```

**Structure Decision**: A new top-level `sim/cloud-deploy/` harness (sibling to 009's `sim/aws-imitation/`)
that deploys the **real** 001 modules — not a hand-authored copy — via thin per-cloud wrapper roots pointing at
Floci. The 001 modules are edited **additively only**: a default-off `emulated` flag and the missing
version/provider constraints. CI keeps the fast gate blocking and adds the heavy deploy as a non-blocking job.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Three local cloud emulators + a per-cloud wrapper harness | The feature's purpose is to prove the real 001 Terraform deploys on each cloud a client can pick; that requires each cloud's emulator + provider wiring | One emulator (AWS only) wouldn't cover GCP/Azure; a single generic harness can't span three provider configs |
| An `emulated` flag added to the 001 modules | GCP/Azure emulators cannot provision managed compute/DB, so the module must be able to skip them locally while staying identical for real cloud | Targeted apply (`-target`) is brittle and doesn't validate the module as a unit; a separate emulator-only config duplicates and drifts from the real module |

## Phase 0 — Research (see research.md)

Resolves: emulator choice (Floci ×3), deploying the real 001 modules via wrapper roots, the additive `emulated`
compat flag, the **empirical coverage probe** (FR-011, task 1) and expected per-cloud coverage, the pinned
containerized Terraform version (1.9.8), the tflint gate fix (additive constraints), CI wiring (blocking gate +
non-blocking deploy job), and Terraform-native apply→state→destroy verification.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the Cloud Target, Coverage Map, `emulated` flag, and Deploy-Check Run entities.
- **contracts/**: `probe.sh` (coverage map), `deploy-check.sh <cloud>` (apply→verify→destroy), the `emulated`
  module-flag semantics, and the CI job contract (blocking gate + non-blocking deploy).
- **quickstart.md**: fix-verify the gate locally; run `sim/cloud-deploy/run-all.sh`; expected pass + how CI runs it.

**Post-design constitution re-check**: PASS — no new violations; the `emulated` flag's default-off invariant
keeps real-cloud behavior unchanged (III), and Terraform-native verification keeps the harness simple (VI).
