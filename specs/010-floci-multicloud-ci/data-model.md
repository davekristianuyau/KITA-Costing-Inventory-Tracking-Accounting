# Data Model — Fix CI Infra + Local Multi-Cloud Terraform Deploy via Floci

This feature is infra tooling; its "entities" are configuration objects and run records, not persisted data.

## Cloud Target

One per supported cloud. Drives the emulator + wrapper root + module.

| Field | Type | Notes |
|---|---|---|
| `cloud` | enum `aws` \| `gcp` \| `azure` | the provider |
| `emulator_image` | string | `floci/floci:latest` \| `floci-io/floci-gcp` \| `floci-io/floci-az` |
| `emulator_port` | int | 4566 (aws) \| 4588 (gcp) \| 4577 (azure) |
| `endpoint` | string | `http://<emulator-host>:<port>` injected into the provider block |
| `module_path` | string | `infra/terraform/modules/<cloud>` (the real 001 module) |
| `wrapper_root` | string | `sim/cloud-deploy/<cloud>/` (provider→emulator + `module {}` call) |

## Coverage Map (produced by the probe — FR-011)

Per cloud, the empirically-measured deploy support. Output of `probe.sh <cloud>`; drives the `emulated` guards.

| Field | Type | Notes |
|---|---|---|
| `cloud` | enum | provider |
| `resource_type` | string | e.g. `aws_db_instance`, `google_sql_database_instance`, `azurerm_container_app` |
| `status` | enum `supported` \| `unsupported` | did a minimal `apply` of this type succeed against the emulator? |
| `note` | string | error class if unsupported (e.g. "API not implemented") |

- Uniqueness: (`cloud`, `resource_type`).
- The set of `unsupported` types per cloud == the set guarded by `emulated = true` in that module.

## `emulated` module flag

Added to each 001 module (`aws`/`gcp`/`azure`).

| Field | Type | Notes |
|---|---|---|
| name | `emulated` | Terraform `variable`, `bool` |
| default | `false` | **real-cloud deploy is unchanged** (FR-005) |
| effect when `true` | — | resources in that module's Coverage Map with `status = unsupported` are skipped (via `count`/`for_each = 0`); everything else applies |
| invariant | — | with `emulated = false`, `terraform plan` output is byte-for-byte identical to before this feature |

## Deploy-Check Run

One apply→verify→destroy cycle for one cloud; the unit CI and the local command report.

| Field | Type | Notes |
|---|---|---|
| `cloud` | enum | provider under test |
| `phase` | enum `emulator-up` \| `init` \| `apply` \| `verify` \| `destroy` | current step |
| `applied_resources` | list<string> | from `terraform state list` between apply and destroy |
| `outcome` | enum `pass` \| `fail` | pass = apply 0 + expected resources present + destroy 0 + no residue |
| `credentials` | const `dummy` | only `test`/dummy creds ever used (SC-003) |

## Relationships

- One **Cloud Target** → one **Coverage Map** (many resource-type rows) → parameterizes one **`emulated` flag**
  setting → each **Deploy-Check Run** applies the supported set for that target.
- All three Cloud Targets are exercised by `run-all.sh` and the non-blocking CI `cloud-deploy` job.

## Not modeled / out of scope

- Real cloud accounts, credentials, or state backends (never used here; feature 001 owns real deploys).
- Runtime/data behavior of the deployed services (the emulators prove the control-plane/Terraform path only).
