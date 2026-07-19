# Floci GCP coverage — 001 `modules/gcp` (measured 2026-07-19, corrected)

Method: point the Terraform `google` provider at Floci-GCP (`floci/floci-gcp:latest`, :4588) via per-service
`*_custom_endpoint` + a dummy `GOOGLE_OAUTH_ACCESS_TOKEN`, then apply resources against it.

## C2 (provider → emulator wiring): RESOLVED ✅

The `google` provider targets Floci-GCP fine — all six `*_custom_endpoint` fields validate and work
(`storage`, `secret_manager`, `compute`, `sql`, `vpc_access`, `service_networking`), dummy token bypasses auth.

## Coverage — most of the module's services work; only Compute/VPC is missing

Floci-GCP's Terraform compat tests + a live spike confirm:

| 001 GCP resource / service | Floci-GCP |
|---|---|
| `google_storage_bucket` (Cloud Storage) | ✅ applied live |
| `google_secret_manager_secret` + `_version` | ✅ applied live |
| `google_cloud_run_v2_service` (Cloud Run) | ✅ **applied live** |
| `google_sql_database_instance` (Cloud SQL PostgreSQL) | ✅ **supported** (repo compat tests) |
| `google_compute_network` / `_subnetwork` / `_global_address` (**Compute Engine VPC**) | ❌ **HTTP 405 — not emulated** |
| `google_vpc_access_connector`, `google_service_networking_connection` | ❌ (depend on the VPC) |

## Correction to an earlier note

An earlier version of this file said Floci-GCP supported "only Storage + Secret Manager." **That was wrong** —
it came from a single `terraform apply` that halted at the first error (`google_compute_network` 405), so Cloud
SQL and Cloud Run were never attempted. Direct testing shows **Cloud Run applies** and the repo confirms **Cloud
SQL** works. The **only** genuine gap is **Compute Engine VPC networking** (`google_compute_network` → 405).

## Implication (GCP is viable — deferred to implement)

The 001 GCP module is blocked only by its **private-VPC foundation**: Cloud SQL uses a *private IP* (needs the
VPC) and Cloud Run reaches it via a *VPC connector*. An `emulated` flag that skips the Compute/VPC resources and,
in emulated mode, uses **Cloud SQL public IP** + **Cloud Run without the connector** would deploy Cloud SQL +
Cloud Run + Storage + Secret Manager — a real app+DB deploy, not a stub. Implementation is deferred to a future
spec; `fmt`/`validate` of the real module still pass today.
