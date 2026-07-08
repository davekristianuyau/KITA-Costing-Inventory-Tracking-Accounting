# Implementation Plan: Multi-Cloud CI/CD Infrastructure Scaffolding

**Branch**: `001-multi-cloud-cicd` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-multi-cloud-cicd/spec.md`

## Summary

Stand up the foundational infrastructure-as-code and delivery pipeline that provisions
and deploys the KITA application (an Odoo/SAP-HANA-class web app + PostgreSQL database +
object storage) onto a client-selected public cloud — AWS, Google Cloud, or Azure — with
provider selection driven by configuration only. Each deployment has two isolated tiers,
STG and PROD, named `{client-name}-{env}`, with gated (non-automatic) promotion of the
same validated artifact from STG to PROD, auto-rollback on failed health checks, a single
default region (per-client override), daily backups + point-in-time recovery on PROD, and
public TLS ingress governed by application-level authentication with per-client custom
domains.

Technical approach: a single Terraform codebase using a **provider-abstraction pattern** —
one thin root configuration selects a per-cloud module (`aws` / `gcp` / `azure`) that all
implement an identical variable/output contract — plus a CI/CD pipeline (GitHub Actions)
whose deploy/promote/teardown jobs call provider-agnostic scripts over that contract. The
same container image tag is promoted STG→PROD; managed services are preferred on every
cloud to minimize operational burden for a solo maintainer.

## Technical Context

**Language/Version**: Terraform (HCL) >= 1.9; Bash scripts (orchestration); YAML (GitHub Actions).
**Primary Dependencies**: Terraform providers `hashicorp/aws` (~> 5.x), `hashicorp/google`
(~> 5.x), `hashicorp/azurerm` (~> 4.x); the target cloud CLIs (`aws`, `gcloud`, `az`) for
auth/verification; a container image built upstream (consumed, not built here).
**Storage**: Managed PostgreSQL per cloud (AWS RDS / GCP Cloud SQL / Azure Database for
PostgreSQL — Flexible Server); managed object storage (S3 / GCS / Azure Blob); Terraform
remote state per cloud (S3+DynamoDB lock / GCS / Azure Storage), state keyed per
`{client-name}-{env}`.
**Testing**: `terraform fmt`/`validate`, `tflint`, static config-contract checks (required
variables + naming regex), `terraform plan` diff review, and post-deploy smoke tests
(health endpoint + DB read/write + isolation) run in CI.
**Target Platform**: AWS, Google Cloud, Azure public regions. Compute via managed container
runtimes — AWS ECS Fargate (behind ALB), GCP Cloud Run, Azure Container Apps — each behind
the module contract.
**Project Type**: Infrastructure + delivery pipeline (no application source in this feature).
**Performance Goals**: A full clean-account provision + deploy completes in < 30 minutes;
a promotion (image swap on existing infra) completes in < 10 minutes. (App runtime
performance is out of scope — deferred to the application feature.)
**Constraints**: Provider switch requires config-only changes (no pipeline/app logic
changes); idempotent re-runs produce zero drift; no secrets in repo/logs; STG and PROD
fully isolated; auto-rollback on failed PROD health check; single default region with
per-client override; data stays in its region.
**Scale/Scope**: Single-tenant per client; near-term focus on local clients (single-region
sufficient). Tens of client deployments expected, each with 2 environments. Each environment
is a single always-on app instance + single-node managed DB (STG sized smaller than PROD).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Approved spec + this plan verifies compliance before build | PASS — spec.md complete and clarified |
| II. Test-Driven Development | Contract/validation tests and post-deploy smoke tests written before the modules/pipeline they cover | PASS — TDD order enforced in tasks; see contracts/ and Testing |
| III. Security & Data Integrity First | Secrets only in cloud secret managers; encryption at rest + TLS in transit; per-env secret scoping; config validated at pipeline entry | PASS — FR-013/014/014a; money-math is app-level (out of scope here) |
| IV. Environment Isolation | STG/PROD separate resources + separate remote state; no shared DB/storage; explicit promotion | PASS — FR-008/008a/009/010; state keyed per {client}-{env} |
| V. Observability & Debuggability | Structured logs shipped to each cloud's log service; health endpoint gates deploy; deploy/promotion audit records | PASS — FR-018/016 |
| VI. Simplicity & YAGNI | Managed services over self-hosting; single-node DB; no Kubernetes; provider abstraction is the minimum needed for the multi-cloud requirement | PASS with justification — see Complexity Tracking |
| VII. Automated Quality Gates | CI runs fmt/validate/tflint/plan/smoke; fail-fast; no merge/promote on failure | PASS — FR-006/006a |

Initial Constitution Check: **PASS** (one justified complexity — multi-cloud provider modules).
Post-Design Constitution Check (after Phase 1): **PASS** — the module-interface contract keeps
provider detail isolated, remote state is keyed per `{client}-{env}`, and no new principle
tension was introduced by the data model or contracts.

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-cloud-cicd/
├── plan.md              # This file
├── spec.md              # Feature spec (with Clarifications)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── config-schema.md         # Required input variables + validation (the tfvars contract)
│   ├── module-interface.md      # Common inputs/outputs every per-cloud module MUST implement
│   └── pipeline-operations.md   # deploy / promote / teardown pre/postconditions & gates
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
infra/
└── terraform/
    ├── main.tf                 # Root: selects the active per-cloud module by var.cloud_provider
    ├── variables.tf            # The config contract (client_name, env, region, app_image, ...)
    ├── outputs.tf              # Common outputs (app_url, db_endpoint_ref, ...)
    ├── versions.tf             # Terraform + provider version pins
    ├── backends/               # Remote-state backend config per cloud (state key = {client}-{env})
    │   ├── aws.tfbackend
    │   ├── gcp.tfbackend
    │   └── azure.tfbackend
    ├── modules/
    │   ├── common/             # Naming ({client}-{env}), tags/labels, validation locals
    │   ├── aws/                # ECS Fargate + ALB + RDS PostgreSQL + S3 + Secrets Manager + ACM
    │   ├── gcp/                # Cloud Run + Cloud SQL + GCS + Secret Manager + managed cert
    │   └── azure/              # Container Apps + Azure DB for PostgreSQL + Blob + Key Vault
    └── environments/           # Per-deployment variable files
        └── <client-name>/
            ├── stg.tfvars
            └── prod.tfvars

scripts/
├── deploy.sh                   # Provision + deploy to a {client}-{env}; runs smoke test
├── promote.sh                  # Promote STG-validated version → PROD (enforces gate)
├── teardown.sh                 # Destroy a {client}-{env} and confirm resource removal
└── validate-config.sh          # Enforce config-schema contract before any plan/apply

tests/
├── contract/                   # Naming-regex, required-variable, and module-interface checks
├── integration/                # Post-deploy smoke tests (health endpoint, DB read/write, isolation)
└── fixtures/                   # Sample tfvars for a throwaway test client

.github/
└── workflows/
    ├── deploy-stg.yml          # On merge to main: deploy/refresh STG, run smoke tests
    ├── promote-prod.yml        # Manual dispatch w/ approval: promote validated version to PROD
    └── teardown.yml            # Manual dispatch: destroy a named {client}-{env}
```

**Structure Decision**: Infrastructure + pipeline layout. All infra lives under
`infra/terraform/` with a provider-abstraction module tree (`modules/{common,aws,gcp,azure}`);
orchestration lives in `scripts/`; quality gates and CI live in `tests/` and
`.github/workflows/`. This satisfies FR-019 (everything version-controlled and reproducible)
and keeps provider-specific detail confined to the three cloud modules behind one contract.

## Complexity Tracking

> Only the one complexity that appears to strain the Simplicity principle is recorded.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Three parallel per-cloud implementation modules (`aws`/`gcp`/`azure`) behind a common contract | The core requirement (FR-002/FR-003) is that a client can pick any of the three clouds with config-only changes; provider APIs genuinely differ, so an abstraction layer is unavoidable | A single cloud (simplest) fails the multi-cloud requirement outright. A cross-cloud abstraction (Kubernetes on all three, Crossplane) adds a heavier always-on control plane and steeper operations than a solo maintainer should carry, and still needs per-cloud config — net more complex than three focused Terraform modules sharing one interface contract. |
