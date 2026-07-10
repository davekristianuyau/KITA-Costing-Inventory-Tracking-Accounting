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
├── environments/<client>/{stg,prod}.tfvars   per-deployment config (the Release Set)
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

Create `environments/<client>/<env>.tfvars` (see `environments/acme/stg.tfvars` for a template):

```hcl
cloud_provider = "aws"        # aws | gcp | azure
client_name    = "acme"
env            = "stg"        # stg | prod
region         = "us-east-1"
custom_domain  = "erp.acme.example"   # optional; served at the public gateway with TLS
release_set = {
  frontend           = { image = "ghcr.io/kita/frontend",           version = "0.1.0", visibility = "public",  port = 8080, health_path = "/" }
  gateway            = { image = "ghcr.io/kita/gateway",            version = "0.1.0", visibility = "public",  port = 8081, health_path = "/actuator/health" }
  operations-service = { image = "ghcr.io/kita/operations-service", version = "0.1.0", visibility = "private", port = 8083, health_path = "/actuator/health" }
}
```

Rules: pin **immutable** versions (no `latest`); at least one service must be `public`; secrets
never go in tfvars. Validate anytime: `scripts/validate-config.sh --client acme --env stg`.

---

## Deploy — step by step

`scripts/deploy.sh` runs the whole flow: it reads `cloud_provider` from the tfvars, initializes the
matching remote-state backend with a per-environment key (`{client}-{env}`), applies the Release Set,
prints the gateway URL, and curls the aggregate health endpoint. Do this once per environment.

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
2. **Write the environment file** `environments/<client>/<env>.tfvars` with `cloud_provider` set to
   the target cloud and `env` to `stg` or `prod` (see [Configure a deployment](#configure-a-deployment)).
3. **Validate the config** (no cloud calls):
   ```bash
   scripts/validate-config.sh --client acme --env stg
   ```
4. **Deploy**:
   ```bash
   scripts/deploy.sh --client acme --env stg
   ```
   On success it prints `deployed acme-stg (<cloud>): https://…` and `healthy`.

### Per-cloud specifics

Everything below is identical *except* the credential step and the two cloud-specific tfvars fields.

**AWS** — `region` is an AWS region (`us-east-1`). Nothing else cloud-specific.
```bash
export AWS_PROFILE=kita          # or AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
scripts/deploy.sh --client acme --env stg
```

**Google Cloud** — set `gcp_project` and use a GCP `region` (`us-central1`). Enable the APIs once:
```bash
gcloud auth application-default login
gcloud services enable run.googleapis.com sqladmin.googleapis.com \
  secretmanager.googleapis.com compute.googleapis.com vpcaccess.googleapis.com \
  servicenetworking.googleapis.com
# in the tfvars: cloud_provider = "gcp", region = "us-central1", gcp_project = "my-project-id"
scripts/deploy.sh --client acme --env stg
```

**Azure** — use an Azure `region` (`eastus`). No extra tfvars field; the module creates its own
resource group, VNet, and Key Vault.
```bash
az login
az account set --subscription <SUBSCRIPTION_ID>
# in the tfvars: cloud_provider = "azure", region = "eastus"
scripts/deploy.sh --client acme --env stg
```

### Deploy to PROD

Copy the STG tfvars to `prod.tfvars`, set `env = "prod"`, keep the **same image versions** you
validated in STG, then deploy. PROD gets its own isolated state key (`acme-prod`); the modules also
harden PROD automatically — AWS enables RDS deletion protection + a final snapshot, and Azure raises
the DB backup retention to a 7-day minimum.
```bash
cp environments/acme/stg.tfvars environments/acme/prod.tfvars
# edit prod.tfvars: env = "prod"  (optionally size = "standard", a real custom_domain)
scripts/deploy.sh --client acme --env prod
```

### Switch clouds

Change `cloud_provider` (and `region`, plus `gcp_project` for GCP) in the tfvars, authenticate to the
new cloud, and re-run `deploy.sh` — no module or Release-Set changes needed. Note this provisions a
fresh stack on the new cloud; it does not migrate data from the old one.

### Update, roll back, tear down

```bash
# Update / roll back: bump (or revert) the version fields in the tfvars, then re-deploy.
scripts/deploy.sh --client acme --env stg

# Tear down an environment (destroys its stack; state store is left intact):
cd infra/terraform
terraform destroy -var-file=environments/acme/stg.tfvars
```

### Manual equivalent (what deploy.sh runs)

```bash
cd infra/terraform
# AWS
terraform init -reconfigure -backend-config=backends/aws.tfbackend \
  -backend-config="key=acme-stg/terraform.tfstate"
# GCP:   -backend-config=backends/gcp.tfbackend   -backend-config="prefix=acme-stg"
# Azure: -backend-config=backends/azure.tfbackend -backend-config="key=acme-stg.tfstate"
terraform apply -var-file=environments/acme/stg.tfvars
terraform output -raw gateway_url
```

---

## Adding a new cloud or service

- **New service** — add an entry to `release_set` in the tfvars (image/version/visibility/port/
  health). Every provider module iterates the set, so no module change is needed.
- **New cloud** — implement `modules/<cloud>/` against the same inputs/outputs as `modules/aws`
  (see `../../specs/001-multi-cloud-cicd/contracts/module-interface.md`), then it's selectable via
  `cloud_provider`.

## Current limitations

- All three modules (**AWS/GCP/Azure**) are implemented and `terraform validate`-clean, but no
  environment has been `apply`-ed yet (no credentials in the dev environment). Real provisioning +
  smoke tests run once credentials and remote state exist.
- Only `environments/acme/stg.tfvars` ships as a sample; create `prod.tfvars` (and other clients)
  as shown above.
- Promotion and teardown are done via re-deploy / `terraform destroy` today; dedicated gated
  `promote.sh`/`teardown.sh` scripts and CI workflows arrive with the lifecycle increment (see
  `../../specs/001-multi-cloud-cicd/tasks.md`).
