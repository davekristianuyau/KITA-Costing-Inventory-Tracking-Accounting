# Floci GCP coverage — 001 `modules/gcp` (measured 2026-07-19)

Method: point the Terraform `google` provider at Floci-GCP (`floci/floci-gcp:latest`, :4588) via per-service
`*_custom_endpoint` + a dummy `GOOGLE_OAUTH_ACCESS_TOKEN`, then `terraform apply` the real 001 GCP module.

## C2 (provider → emulator wiring): RESOLVED ✅

The `google` provider **can** target Floci-GCP. All six per-service endpoint fields validate and work:
`storage_custom_endpoint`, `secret_manager_custom_endpoint`, `compute_custom_endpoint`,
`sql_custom_endpoint`, `vpc_access_custom_endpoint`, `service_networking_custom_endpoint`. A dummy OAuth token
bypasses real auth. `sim/cloud-deploy/gcp/main.tf` exercises the supported services live.

## Coverage: NARROW (unlike AWS)

| 001 GCP resource | Floci-GCP |
|---|---|
| `google_storage_bucket` | ✅ applied |
| `google_secret_manager_secret` + `_version` | ✅ applied |
| `random_password` | ✅ |
| `google_compute_network` | ❌ **HTTP 405** — Compute API not implemented |
| `google_compute_subnetwork`, `google_compute_global_address` | ❌ (Compute) |
| `google_vpc_access_connector` | ❌ (depends on network) |
| `google_service_networking_connection` | ❌ (depends on network) |
| `google_sql_database_instance` / `_database` / `_user` | ❌ (private-IP needs the VPC network) |
| `google_cloud_run_v2_service` (+ IAM) | ❌ (needs the VPC connector) |

## Implication

Floci-GCP implements **Storage + Secret Manager** (and messaging/data services), but **not the Compute
control-plane** the 001 GCP module is built on (VPC → Cloud SQL private IP → Serverless VPC connector → Cloud
Run). So — unlike AWS, which deploys almost entirely — the **real 001 GCP module cannot deploy against Floci**;
only its storage + secret slice can.

Consequently, heavily guarding the GCP module with `emulated` (skipping ~10 of ~13 resources + a large
reference cascade) would complicate the production module for a 2-resource local apply of low value. The
GCP/Azure deploy strategy therefore needs a scope decision (see spec/plan) rather than forcing the full-module
guard. `fmt`/`validate`/`plan` of the real module still pass (proving it's deployable); only the local `apply`
is bounded to the emulator-supported slice.
