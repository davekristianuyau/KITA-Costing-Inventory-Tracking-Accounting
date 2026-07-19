# Local multi-cloud Terraform deploy via Floci (feature 010)

Deploys the **real** feature-001 Terraform modules against local [Floci](https://floci.io/) emulators —
`apply → verify → destroy` — with **no real cloud credentials or spend**. Everything runs in containers
(Floci + a pinned `hashicorp/terraform:1.9.8` runner + `aws-cli`), so no host `terraform`/cloud-CLI is needed.

## Usage

```bash
bash sim/cloud-deploy/deploy-check.sh aws   # start Floci → apply modules/aws → state list → destroy
bash sim/cloud-deploy/run-all.sh            # all currently-supported clouds (AWS), then tear down
bash sim/cloud-deploy/probe.sh aws          # (re)measure coverage → coverage/aws.md
```

CI runs `run-all.sh` as a **non-blocking** `cloud-deploy` job; the fast fmt/validate/tflint `infra` gate stays
blocking.

## Per-cloud coverage (measured — FR-011)

| Cloud | Floci image | Deploys the 001 module? | Notes |
|---|---|---|---|
| **AWS** | `floci/floci:latest` :4566 | ✅ **near-complete** | VPC/NAT/ECS/ALB/ServiceDiscovery/IAM/S3/Secrets all apply. Only `aws_db_instance` (RDS) is skipped when `emulated` — Floci emulates RDS's ~15-min provisioning (too slow), not a capability gap. See [coverage/aws.md](coverage/aws.md). |
| **GCP** | `floci/floci-gcp:latest` :4588 | ⚠️ **deferred (viable)** | Provider→Floci works. Storage, Secret Manager, **Cloud SQL, and Cloud Run** all work; the **only** gap is Compute Engine **VPC networking** (`google_compute_network` → 405). The module is blocked just by its private-VPC foundation; skipping Compute/VPC + Cloud SQL public IP would deploy the app+DB. See [coverage/gcp.md](coverage/gcp.md). |
| **Azure** | `floci/floci-az:latest` :4577 | ⚠️ **deferred (viable)** | Richest emulator: **VNet + PostgreSQL Flexible + Key Vault + Storage + Resource Group** all supported; gap is **Container Apps** (compute) + Log Analytics + Private DNS. Needs TLS (`FLOCI_AZ_TLS_ENABLED`) + trusting the self-signed CA. See [coverage/azure.md](coverage/azure.md). |

## The `emulated` module flag (AWS)

`infra/terraform/modules/aws` has an additive `variable "emulated"` (default **false** ⇒ real-cloud deploy
unchanged, FR-005). When `true`, it skips the slow RDS (`count = var.emulated ? 0 : 1`) and its references fall
back to placeholders (the DB secret URL, the `resource_ids.database` output). The deploy-check sets it true; a
real deploy leaves it false and creates the full module (verified: `plan emulated=false` = 42 resources incl.
`aws_db_instance.main[0]`).

## No real cloud

Only dummy credentials (`test` for AWS; a dummy `GOOGLE_OAUTH_ACCESS_TOKEN` for GCP) and local emulator
endpoints are ever used. Terraform state is ephemeral and gitignored.
