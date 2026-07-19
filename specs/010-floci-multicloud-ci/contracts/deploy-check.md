# Contract — Deploy Check (`sim/cloud-deploy/deploy-check.sh <cloud>`)

Deploys the **real** 001 module for one cloud against its Floci emulator: apply → verify → destroy, with no real
cloud. This is the per-cloud "does it stand up?" check (US2/US3).

## Interface

- **Invocation**: `bash sim/cloud-deploy/deploy-check.sh <aws|gcp|azure>`; `run-all.sh` runs all three (SC-004).
- **Steps**:
  1. Start that cloud's Floci emulator (Compose); wait (bounded) for readiness — timeout → clear failure, no hang.
  2. `terraform init` + `terraform apply -auto-approve` on `sim/cloud-deploy/<cloud>/` (wrapper root → the 001
     module with `emulated = true`), using the pinned `hashicorp/terraform:1.9.8` container + dummy creds.
  3. **Verify**: `terraform state list` contains the expected supported resources for that cloud.
  4. `terraform destroy -auto-approve`; confirm no residual resources/state/containers.
- **Exit code**: `0` = apply succeeded + expected resources present + destroy succeeded + no residue; non-zero
  otherwise (with the failing phase + cloud named).

## Contract tests (what must hold)

- **Apply**: the wrapper root applies cleanly against the emulator (the emulator-supported resource set).
- **Isolation**: only dummy creds + the local emulator endpoint appear; no real cloud contacted (SC-003).
- **Destroy/no-residue**: after the run, `terraform state list` is empty and the emulator container is gone
  (SC-006).
- **Regression catch (SC-005)**: introducing a breaking change into the module makes `deploy-check.sh` fail —
  proving the check deploys, not just validates.
- **Real-cloud invariant**: with `emulated = false`, `terraform plan` of the 001 module is unchanged vs. before
  this feature (guard defaults off — FR-005).

## Acceptance

- Each of `aws`, `gcp`, `azure` returns `pass`; `run-all.sh` returns an overall pass and completes < 15 min.
