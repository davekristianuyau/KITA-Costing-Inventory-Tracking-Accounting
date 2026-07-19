# Contract — Coverage Probe (`sim/cloud-deploy/probe.sh <cloud>`)

Empirically measures which of a cloud's 001-module resource types Floci can provision (FR-011). Runs **first**
(task 1); its output decides the `emulated` guards.

## Interface

- **Invocation**: `bash sim/cloud-deploy/probe.sh <aws|gcp|azure>` (no host Terraform/cloud-CLI; containerized).
- **Behavior**: start that cloud's Floci emulator → for each resource type used by the 001 module, attempt a
  minimal `terraform apply` (isolated) against the emulator with dummy creds → record `supported` /
  `unsupported` → `terraform destroy` → tear the emulator down.
- **Output**: a per-cloud coverage map (see data-model.md) written to
  `sim/cloud-deploy/coverage/<cloud>.md` (human-readable table) — committed as the documented boundary (FR-009).
- **Exit code**: `0` if the probe completed and produced a map (regardless of how many types are unsupported);
  non-zero only if the emulator or Terraform runner itself failed to run.

## Acceptance

- Produces a coverage map naming every resource type in the module as `supported` or `unsupported`.
- Uses only dummy credentials + the local emulator endpoint (no real cloud).
- Leaves no emulator container, resources, or Terraform state behind.
- The `unsupported` set for a cloud exactly matches the resources its module guards with `emulated = true`.
