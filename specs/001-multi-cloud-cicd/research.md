# Phase 0 Research: Multi-Cloud CI/CD Infrastructure Scaffolding

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

This document resolves the technical unknowns implied by the spec and clarifications.
Each entry records the decision, rationale, and alternatives considered.

## R1. Multi-cloud IaC structure

- **Decision**: Single Terraform codebase using a **provider-abstraction pattern**: a thin
  root config (`main.tf`) selects exactly one per-cloud module (`modules/aws|gcp|azure`)
  based on `var.cloud_provider`; every module implements an identical input/output
  contract (see `contracts/module-interface.md`). Shared naming/tagging/validation lives
  in `modules/common`.
- **Rationale**: Directly satisfies FR-002/FR-003 (config-only provider switch). Keeps
  provider-specific resources encapsulated so the pipeline and app never change when the
  target cloud changes. A single codebase (vs three) keeps the "minimal changes"
  guarantee auditable in one place.
- **Alternatives considered**:
  - *Three independent codebases* — rejected: duplicates pipeline logic and makes the
    "minimal changes" claim unverifiable.
  - *Kubernetes (EKS/GKE/AKS) as a uniform layer* — rejected: heavy always-on control
    plane and operational load inappropriate for a solo maintainer (Simplicity principle).
  - *Crossplane / Pulumi multi-cloud abstractions* — rejected: adds a new control plane /
    runtime and still requires per-cloud config; net more complex than three Terraform
    modules behind a contract.

## R2. Compute (application runtime) per cloud

- **Decision**: Managed container runtimes with always-on capability and native TLS
  ingress + custom domains: **AWS ECS Fargate behind an ALB**, **GCP Cloud Run** (min
  instances ≥ 1), **Azure Container Apps**. All consume the same container image.
- **Rationale**: All three are serverless-container services (no node management), support
  a single always-on web app, HTTPS ingress, custom domains, revisions, and rolling
  updates with health checks — the primitives needed for FR-001/FR-001b and auto-rollback
  (FR-006a). Preferring managed services satisfies the Simplicity principle.
- **Alternatives considered**:
  - *VMs (EC2/GCE/Azure VM)* — rejected: OS patching and process supervision add ongoing toil.
  - *AWS App Runner* — viable but ALB+Fargate gives finer control over health checks and
    blue/green rollback; chosen for parity with the other two.

## R3. Database per cloud

- **Decision**: Managed **PostgreSQL**: AWS RDS for PostgreSQL, GCP Cloud SQL for
  PostgreSQL, Azure Database for PostgreSQL (Flexible Server). Single node. PROD enables
  daily automated backups + point-in-time recovery; STG uses relaxed backups.
- **Rationale**: PostgreSQL is available managed on all three clouds and is the native
  database of the Odoo-class application referenced in the spec. Managed PITR maps
  directly to the clarified recovery expectation (FR-014a). Single node matches the
  chosen recovery model (no HA standby) and controls cost.
- **Alternatives considered**:
  - *Self-managed PostgreSQL on compute* — rejected: backups/patching become manual.
  - *Cloud-native non-portable databases (Aurora-only features, Spanner, Cosmos DB)* —
    rejected: would break portability and lock the schema to one provider.
  - *HA multi-node* — deferred: clarification chose single-node + PITR; can be added later
    via a sizing/HA variable without changing the contract.

## R4. Object storage, secrets, TLS/domains

- **Decision**: Object storage = S3 / GCS / Azure Blob. Secrets = AWS Secrets Manager /
  GCP Secret Manager / Azure Key Vault, scoped per environment. TLS = provider-managed
  certificates (ACM / Google-managed certs / Azure Container Apps managed certs) bound to
  the client's custom domain.
- **Rationale**: Each is the first-party managed equivalent, satisfies encryption-at-rest
  (FR-014), keeps secrets out of repo/logs (FR-013), and provides automatic cert renewal
  for per-client domains (FR-001b).
- **Alternatives considered**: Storing secrets in CI variables only — rejected: the
  running app also needs runtime secret access, which the cloud secret manager provides
  natively; CI holds only the bootstrap credential.

## R5. Remote state & isolation

- **Decision**: Remote Terraform state per cloud with locking — S3 + DynamoDB lock table
  (AWS), GCS bucket with object versioning (GCP), Azure Storage with blob lease (Azure).
  **State key = `{client-name}-{env}`**, giving each environment its own isolated state.
- **Rationale**: Per-environment state is what makes STG/PROD and per-client isolation
  real (FR-008/FR-009) and prevents concurrent-change conflicts via native locking
  (FR-017). Keying by `{client}-{env}` reuses the naming convention (FR-020).
- **Alternatives considered**:
  - *Single state file with workspaces* — rejected: a blast-radius risk (one corrupt state
    endangers all clients) and weaker isolation than separate state objects.
  - *Local state* — rejected: not durable or lockable (fails the Dependencies section).

## R6. CI/CD pipeline

- **Decision**: **GitHub Actions** with three workflows: `deploy-stg` (auto on merge to
  main), `promote-prod` (manual `workflow_dispatch` gated by a GitHub Environment approval),
  `teardown` (manual). Each job calls provider-agnostic scripts (`deploy.sh`/`promote.sh`/
  `teardown.sh`) so CI holds orchestration, not cloud logic.
- **Rationale**: The repo is Git-based; GitHub Environments provide the explicit approval
  gate that enforces non-automatic promotion (FR-009/FR-010/FR-011) and per-environment
  secret scoping. Keeping logic in scripts keeps the pipeline portable to another CI later.
- **Alternatives considered**: Cloud-native CI (CodePipeline / Cloud Build / Azure
  Pipelines) — rejected: would tie the pipeline itself to one cloud, contradicting the
  multi-cloud goal.

## R7. Artifact build & promotion model

- **Decision**: The application image is **built upstream** (out of scope) and published to
  a single cloud-agnostic registry (e.g., GHCR) referenced by immutable tag/digest.
  Promotion moves the **same digest** from STG to PROD — no rebuild.
- **Rationale**: Promoting the identical artifact is what makes "validated in STG" mean
  something in PROD (FR-010, SC-006). A single registry all clouds can pull from avoids
  per-cloud image copies for the common case.
- **Alternatives considered**: Per-cloud registries (ECR/Artifact Registry/ACR) — noted as
  a future option if a client requires images to stay within their cloud; handled by an
  optional image-mirror step, not needed for the MVP.

## R8. Deployment strategy & auto-rollback

- **Decision**: Rolling update with a health-check gate on each cloud's revision mechanism
  (ECS blue/green via CodeDeploy or rolling with circuit breaker; Cloud Run revision + traffic
  shift; Container Apps revision + traffic weight). On failed health check, traffic stays on
  (or reverts to) the last healthy revision; the new revision receives no traffic.
- **Rationale**: Implements the clarified auto-rollback behavior (FR-006a) using native
  primitives rather than bespoke tooling.
- **Alternatives considered**: Manual rollback only — rejected by clarification (chose
  auto-rollback for a live financial system).

## R9. Region policy

- **Decision**: A single default region applies to all clients; `var.region` overrides it
  per client. Data-bearing resources (DB, storage, backups) are created only in that region.
- **Rationale**: Matches the clarified single-default-region policy with per-client override
  and in-region data (FR-002a); fits the near-term local-client focus.
- **Alternatives considered**: Mandatory per-client region or multi-region HA — rejected by
  clarification; multi-region can be added later behind the same variable.

## Deferred (not blocking)

- **Application runtime performance targets** (latency/throughput): app-level, set when the
  KITA application feature is specified. Infra exposes a sizing variable to meet them later.
