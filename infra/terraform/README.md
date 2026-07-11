# KITA Infrastructure (Terraform)

This directory provisions and deploys the KITA multi-service application to **AWS, Google Cloud,
or Azure** from one Terraform codebase. You pick the cloud with a single config value; the same
pipeline and application deploy to any of the three.

> Status: the **AWS, GCP, and Azure** modules are all implemented and `terraform validate`-clean.
> Nothing has been `apply`-ed yet — that requires cloud credentials and bootstrapped remote state.

---

## Terraform in two minutes

**Terraform** is an infrastructure-as-code tool: you *declare* the resources you want (networks,
databases, containers) in `.tf` files, and Terraform makes the cloud match that declaration.

Core ideas:

- **Providers** — plugins that talk to a cloud's API (`aws`, `google`, `azurerm`). Declared in
  `versions.tf`; downloaded by `terraform init`.
- **Resources** — a thing to create, e.g. `resource "aws_db_instance" "main" { ... }`.
- **Variables / outputs** — inputs (`var.client_name`) and results (`output "gateway_url"`).
- **State** — Terraform records what it created in a **state file** so it knows what to change
  next time. We keep state **remote** (per cloud) and **locked**, so it's shared and safe.
- **Modules** — reusable groups of resources. Here, each cloud is a module implementing the same
  interface, so the root config can swap between them.

The workflow:

```
terraform init      # download providers + configure the backend (state)
terraform validate  # static check: syntax, types, provider schema (no cloud calls)
terraform plan      # preview the changes (reads cloud state — needs credentials)
terraform apply     # make the changes
terraform destroy   # tear it all down
```

`init` and `validate` need no cloud credentials. `plan`/`apply`/`destroy` **do**.

### How this codebase is organized (provider abstraction)

The root config (`main.tf`) selects **exactly one** per-cloud module based on `var.cloud_provider`:

```
root (variables + release_set)  ──selects──▶  modules/aws | modules/gcp | modules/azure
                                                     │  (identical inputs & outputs)
                                                     ▼
                          one public gateway + private backend services + shared PostgreSQL
```

The app is deployed as a **Release Set** — a map of `service → { image, version, visibility, … }`.
Deploy/promote/rollback act on the whole set, so an environment is always version-consistent.
Only `public` services (gateway/frontend) are internet-facing; backend services stay private.

---

## Layout

```
infra/terraform/
├── versions.tf            provider + Terraform version pins
├── variables.tf           config contract (client_name, env, region, release_set, …)
├── main.tf                selects the active per-cloud module
├── outputs.tf             gateway_url, aggregate_health_url, service_endpoints, …
├── backends/              partial remote-state configs (aws/gcp/azure)
├── clouds/{aws,gcp,azure}.tfvars   platform overlays — pick one with `--cloud`, never edit to switch
├── environments/<client>/{stg,prod}.tfvars   cloud-agnostic config (client + the Release Set)
└── modules/
    ├── common/            naming ({client}-{env}) + tags
    ├── aws/               VPC, ECS Fargate (service per Release-Set entry), ALB, RDS, S3, Cloud Map
    ├── gcp/               Cloud Run + Cloud SQL (private IP) + GCS + Secret Manager + VPC connector
    └── azure/             Container Apps + Azure DB for PostgreSQL Flexible + Blob + Key Vault
```

Orchestration scripts live at the repo root under `scripts/` (`validate-config.sh`, `deploy.sh`).

---

## Prerequisites

- **Terraform ≥ 1.9** — https://developer.hashicorp.com/terraform/install
- The CLI for your target cloud:
  - AWS: **AWS CLI v2**
  - GCP: **gcloud**
  - Azure: **az** (Azure CLI)
- Container images for every service in the Release Set, published to a registry the cloud can pull.

Verify: `terraform version`.

---

## Set up credentials

Terraform reads cloud credentials from your environment (the same ones the CLIs use). Never put
secrets in `.tf` or `.tfvars` files.

### AWS
Pick one:
- `aws configure` (writes `~/.aws/credentials`), then optionally `export AWS_PROFILE=<name>`, or
- Environment variables:
  ```bash
  export AWS_ACCESS_KEY_ID=...
  export AWS_SECRET_ACCESS_KEY=...
  export AWS_REGION=us-east-1
  ```
- Or SSO: `aws sso login --profile <name>` + `export AWS_PROFILE=<name>`.

The identity needs permissions for VPC, ECS, ELB, RDS, S3, IAM, Secrets Manager, Cloud Map, ACM,
and CloudWatch Logs (an admin/poweruser role covers a first deployment).

### Google Cloud
```bash
gcloud auth application-default login      # user creds for Terraform (ADC), or
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
gcloud config set project <PROJECT_ID>
```
Pass the project to Terraform via `-var gcp_project=<PROJECT_ID>` (or set it in tfvars). Enable the
Cloud Run, Cloud SQL Admin, Secret Manager, and Compute APIs on the project.

### Azure
```bash
az login                                   # interactive, or a service principal:
export ARM_CLIENT_ID=...
export ARM_CLIENT_SECRET=...
export ARM_TENANT_ID=...
export ARM_SUBSCRIPTION_ID=...
```

---

## Bootstrap remote state (one-time per cloud)

State must live in a durable, lockable backend. Create these once, then reference them in
`backends/<cloud>.tfbackend`:

- **AWS** — an S3 bucket (versioned) + a DynamoDB table (name `kita-tflock`, key `LockID`).
- **GCP** — a GCS bucket (versioning on).
- **Azure** — a storage account + a `tfstate` container in a resource group.

`deploy.sh` supplies the per-environment state key (`{client}-{env}`) at init time, so each
environment gets isolated state.

---

## Configure a deployment

Config is split in two so **you never edit a tfvars file just to change cloud**:

**1. Platform overlays** — `clouds/{aws,gcp,azure}.tfvars` — hold only the cloud + region (and
`gcp_project` for GCP). They ship ready; you pick one at deploy time with `--cloud`. Never edit these
to switch clouds — you just choose a different one.

```hcl
# clouds/aws.tfvars
cloud_provider = "aws"
region         = "us-east-1"
```

**2. Env config** — `environments/<client>/<env>.tfvars` — is **cloud-agnostic**: the client, the
sizing, and the Release Set. The same file deploys to any of the three clouds.

```hcl
# environments/acme/stg.tfvars   (see this file for the full template)
client_name              = "acme"
env                      = "stg"        # stg | prod
size                     = "small"
db_backup_retention_days = 1
custom_domain            = "erp.acme.example"   # optional; served at the public gateway with TLS
release_set = {
  frontend           = { image = "ghcr.io/kita/frontend",           version = "0.1.0", visibility = "public",  port = 8080, health_path = "/" }
  gateway            = { image = "ghcr.io/kita/gateway",            version = "0.1.0", visibility = "public",  port = 8081, health_path = "/actuator/health" }
  operations-service = { image = "ghcr.io/kita/operations-service", version = "0.1.0", visibility = "private", port = 8083, health_path = "/actuator/health" }
}
```

Rules: pin **immutable** versions (no `latest`); at least one service must be `public`; secrets
never go in tfvars. Validate anytime: `scripts/validate-config.sh --client acme --env stg --cloud aws`.

---

## Deploy — step by step

`scripts/deploy.sh --client <c> --env <e> --cloud <aws|gcp|azure>` runs the whole flow: it validates
config, initializes the matching remote-state backend with a per-environment key (`{client}-{env}`),
applies the env Release Set **plus** the chosen platform overlay, prints the gateway URL, and
health-checks it. **Switching cloud = changing the `--cloud` flag** — no file edits.

### 0. One-time per cloud — bootstrap remote state

Create the backend store named in `backends/<cloud>.tfbackend` (edit those files to your own names).

```bash
# AWS — versioned S3 bucket + DynamoDB lock table
aws s3api create-bucket --bucket kita-tfstate --region us-east-1
aws s3api put-bucket-versioning --bucket kita-tfstate --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name kita-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH --billing-mode PAY_PER_REQUEST

# GCP — versioned GCS bucket (state locking is automatic)
gcloud storage buckets create gs://kita-tfstate --location=US
gcloud storage buckets update gs://kita-tfstate --versioning

# Azure — resource group + storage account + container
az group create --name kita-tfstate --location eastus
az storage account create --name kitatfstate --resource-group kita-tfstate --sku Standard_LRS
az storage container create --name tfstate --account-name kitatfstate
```

### Step-by-step (any cloud)

1. **Authenticate** to the target cloud (see [Set up credentials](#set-up-credentials)).
   Confirm: `aws sts get-caller-identity` / `gcloud auth list` / `az account show`.
2. **Write the env file** `environments/<client>/<env>.tfvars` — client, size, Release Set. It's
   cloud-agnostic; you do **not** put the cloud here (see [Configure a deployment](#configure-a-deployment)).
3. **Validate the config** (no cloud calls) — name the platform with `--cloud`:
   ```bash
   scripts/validate-config.sh --client acme --env stg --cloud aws
   ```
4. **Deploy** — pick the platform with `--cloud`:
   ```bash
   scripts/deploy.sh --client acme --env stg --cloud aws
   ```
   On success it prints `deployed acme-stg on aws: https://…` and `healthy`.

### Deploy to a specific platform

Same env file, same command — only `--cloud` changes. Authenticate to that cloud first.

**AWS** (`clouds/aws.tfvars`, region `us-east-1`):
```bash
export AWS_PROFILE=kita          # or AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
scripts/deploy.sh --client acme --env stg --cloud aws
```

**Google Cloud** (`clouds/gcp.tfvars` — set `gcp_project` there once; region `us-central1`). Enable
the APIs once:
```bash
gcloud auth application-default login
gcloud services enable run.googleapis.com sqladmin.googleapis.com \
  secretmanager.googleapis.com compute.googleapis.com vpcaccess.googleapis.com \
  servicenetworking.googleapis.com
scripts/deploy.sh --client acme --env stg --cloud gcp
```

**Azure** (`clouds/azure.tfvars`, region `eastus`; the module creates its own resource group, VNet,
and Key Vault):
```bash
az login
az account set --subscription <SUBSCRIPTION_ID>
scripts/deploy.sh --client acme --env stg --cloud azure
```

To move a client from one cloud to another, just run the command again with a different `--cloud`
(this provisions a fresh stack on the new cloud; it does not migrate data).

### Promote STG → PROD

PROD is promoted from a **validated** STG, never deployed blind. Keep `environments/<client>/prod.tfvars`
with the **same image versions** as `stg.tfvars` (bump STG first, validate, then mirror), then promote
on the target platform:
```bash
scripts/promote.sh --client acme --cloud aws
```
`promote.sh` enforces two gates before touching PROD, then delegates to `deploy.sh`:
1. **Version match** — the PROD Release Set must equal the STG one (same images/versions, no rebuild).
2. **STG healthy** — STG's aggregate-health endpoint must currently be UP.

PROD also hardens automatically vs. STG: it never runs the `small` profile (auto-upgraded to
`standard`), RDS gets deletion protection + a final snapshot, and all three clouds raise DB backup
retention to a 7-day minimum with point-in-time recovery enabled. PROD has its own isolated state
key (`acme-prod`), network, DB, storage, and per-env secrets — nothing is shared with STG.

### Switch clouds

Re-run with a different `--cloud` — no file edits. The env Release Set is cloud-agnostic; the cloud
and region come from `clouds/<cloud>.tfvars`. Authenticate to the new cloud first. This provisions a
fresh stack on the new cloud; it does not migrate data from the old one.
```bash
scripts/deploy.sh --client acme --env stg --cloud gcp   # same client, now on GCP
```

### Update, roll back, tear down

```bash
# Update: bump the version fields in the env tfvars, then re-deploy (same --cloud).
scripts/deploy.sh --client acme --env stg --cloud aws
```
`deploy.sh` is **health-gated**: after `apply` it polls the aggregate-health endpoint, and if the new
Release Set is unhealthy it **auto-rolls-back** to the last-good set (snapshotted under
`infra/terraform/.last-good/`) and exits non-zero. Every deploy is recorded to
`infra/terraform/.deployments/<client>-<env>.log` (FR-016).
```bash
# Tear down an environment (destroys its stack; state store is left intact):
cd infra/terraform
terraform destroy -var-file=environments/acme/stg.tfvars -var-file=clouds/aws.tfvars
```

### Manual equivalent (what deploy.sh runs)

Two `-var-file`s: the cloud-agnostic env config **plus** the platform overlay.
```bash
cd infra/terraform
# AWS
terraform init -reconfigure -backend-config=backends/aws.tfbackend \
  -backend-config="key=acme-stg/terraform.tfstate"
# GCP:   -backend-config=backends/gcp.tfbackend   -backend-config="prefix=acme-stg"
# Azure: -backend-config=backends/azure.tfbackend -backend-config="key=acme-stg.tfstate"
terraform apply -var-file=environments/acme/stg.tfvars -var-file=clouds/aws.tfvars
terraform output -raw gateway_url
```

---

## CI/CD pipeline

Two GitHub Actions workflows under `.github/workflows/` wrap the same `deploy.sh`/`promote.sh` used
locally, so a pipeline run does exactly what you can reproduce by hand. Each takes a **`cloud` input**
(`aws`/`gcp`/`azure`), so one pipeline serves all three platforms — you pick the target when you run it.

### `deploy-stg.yml` — continuous deployment to STG

```
merge to main ──▶ auth (OIDC) for chosen cloud ──▶ deploy.sh --env stg --cloud <cloud> ──▶ health gate ──▶ (auto-rollback on fail)
```

- **Trigger**: push to `main` touching `infra/terraform/**` or `scripts/**` (or manual
  `workflow_dispatch` with `client` + `cloud` inputs). STG is continuously delivered; it is **never** gated.
- **`environment: stg`** — a GitHub Environment with no required reviewers.
- **Choose platform** — the `cloud` dispatch input (push runs use repo variable `DEFAULT_CLOUD`, else
  `aws`); only the matching auth step runs, so the same job deploys to any cloud.
- **Auth via OIDC** — no long-lived keys in the repo. Federate the runner to your cloud and store the
  provider references as repo secrets (see below).
- **Deploy** — `deploy.sh` validates config → `terraform apply` (env + platform overlay) → polls
  aggregate health → auto-rolls-back to the last-good Release Set if unhealthy. A red pipeline means
  STG kept serving the old set.

### `promote-prod.yml` — gated promotion to PROD

```
manual dispatch (client + cloud) ──▶ PROD env approval ──▶ auth ──▶ promote.sh (version-match + STG-healthy gates) ──▶ deploy.sh --env prod
```

- **Trigger**: `workflow_dispatch` only — PROD is **never** touched by a merge (FR-009). A separate
  human action is always required.
- **`environment: prod`** — configure **Required reviewers** on this GitHub Environment
  (Settings → Environments → prod). That approval is the human gate (FR-010); the job pauses until an
  approver clicks through.
- **`promote.sh` gates** — refuses unless (1) the PROD Release Set equals the STG-validated one and
  (2) STG is currently healthy. Only then does it apply PROD (health-gated, with the same
  auto-rollback as STG).

### One-time CI setup

1. **Bootstrap remote state** per cloud (see the deploy steps above) — the runners share it.
2. **Federate the runner** to your cloud with OIDC (no static keys):
   - AWS: an IAM role trusting GitHub's OIDC provider → secret `AWS_DEPLOY_ROLE`.
   - GCP: a Workload Identity Provider + service account → secrets `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA`.
   - Azure: an app registration with federated credentials → secrets `AZURE_CLIENT_ID`,
     `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`.
3. **Create the `stg` and `prod` GitHub Environments**; add **Required reviewers** to `prod`.
4. **Runtime app secrets** (DB credentials, etc.) are created by Terraform in the cloud's secret store
   and are **per-env** (`{client}-{env}`) — STG and PROD never share a secret. Nothing sensitive is
   stored in the repo or in tfvars.

---

## Adding a new cloud or service

- **New service** — add an entry to `release_set` in the tfvars (image/version/visibility/port/
  health). Every provider module iterates the set, so no module change is needed.
- **New cloud** — implement `modules/<cloud>/` against the same inputs/outputs as `modules/aws`
  (see `../../specs/001-multi-cloud-cicd/contracts/module-interface.md`), then it's selectable via
  `cloud_provider`.

## Current limitations

- All three modules (**AWS/GCP/Azure**) are implemented and `terraform validate`-clean, but no
  environment has been `apply`-ed yet (no credentials in the dev environment). Real provisioning and
  the integration/smoke tests (marked *live* in `tests/README.md`) run once credentials and remote
  state exist.
- Gated STG→PROD promotion (`promote.sh`), health-gated deploy with auto-rollback, and the
  `deploy-stg`/`promote-prod` CI workflows are implemented. A dedicated `teardown.sh` script +
  teardown workflow (US5) are still pending; teardown today is `terraform destroy`.
- `environments/acme/{stg,prod}.tfvars` ship as samples; add other clients as shown above.
