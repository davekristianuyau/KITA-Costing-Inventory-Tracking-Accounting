# Implementation Plan: Multi-Cloud CI/CD Infrastructure Scaffolding

**Branch**: `001-multi-cloud-cicd` | **Date**: 2026-07-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-multi-cloud-cicd/spec.md`

## Summary

Provision and deploy KITA — now a **multi-service application** (React/Nginx frontend, Spring
Cloud Gateway, and Spring Boot microservices such as `operations-service`, per features 002/003)
— onto a client-selected public cloud (AWS, GCP, or Azure) with configuration-only provider
switching. Terraform (single codebase, provider-abstraction modules) provisions per environment:
a private network, per-service compute for every service in a **Release Set**, a shared managed
PostgreSQL, object storage, service-to-service networking, and a single public gateway/ingress
(TLS + client custom domain). Only the gateway (and frontend) are public; backend services are
private. Two isolated tiers (STG, PROD) with gated, whole-Release-Set promotion, auto-rollback on
failed aggregate health, single default region, per-`{client-name}-{env}` naming, and daily
backups + PITR on PROD. CI/CD (GitHub Actions) runs provider-agnostic scripts over the module
contract.

## Technical Context

**Language/Version**: Terraform (HCL) >= 1.9; Bash (orchestration); YAML (GitHub Actions).
**Primary Dependencies**: Terraform providers `hashicorp/aws` (~> 5.x), `hashicorp/google`
(~> 5.x), `hashicorp/azurerm` (~> 4.x); cloud CLIs; container images (a Release Set) built by
features 002/003 and published to a registry.
**Storage**: Managed PostgreSQL per environment (RDS / Cloud SQL / Azure DB for PostgreSQL,
shared by services); object storage (S3/GCS/Blob); remote Terraform state per cloud keyed
`{client-name}-{env}`.
**Testing**: `terraform fmt`/`validate`, `tflint`, config-contract checks (release-set schema,
naming regex), `plan` diff, and post-deploy smoke tests (public gateway health, private-service
reachability, aggregate health, per-service round trip).
**Target Platform**: AWS, GCP, Azure. Multi-service compute — AWS ECS Fargate services behind one
ALB in a VPC; GCP Cloud Run services (+ Serverless VPC connector, internal ingress for backend);
Azure Container Apps environment (external/internal ingress). Managed PostgreSQL private per env.
**Project Type**: Infrastructure + delivery pipeline for a multi-service application.
**Performance Goals**: Clean-account provision + deploy of the full service set < 45 minutes; a
Release-Set promotion (image swaps on existing infra) < 15 minutes.
**Constraints**: Provider switch is config-only; idempotent; no secrets in repo/logs; STG/PROD
isolated (network, DB, storage, service workloads); only the gateway public (backend private,
FR-001c); deploy/promote/rollback operate on the whole Release Set (FR-022); aggregate health
gates success and drives auto-rollback (FR-004/006a/SC-014); single default region, in-region data.
**Scale/Scope**: Single-tenant per client; tens of clients × 2 tiers. Each environment runs a
handful of services (frontend, gateway, 1+ backend services) + one managed DB.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified multi-service spec + this plan | PASS |
| II. Test-Driven Development | Contract/validation + post-deploy smoke (incl. private-vs-public + aggregate health) written before modules/pipeline | PASS |
| III. Security & Data Integrity First | Secrets in cloud secret managers per service/env; encryption at rest + TLS; backend services private; PROD PITR | PASS — FR-001c/013/014/014a |
| IV. Environment Isolation | STG/PROD separate resources + state; no shared network/DB/storage/services; gated promotion | PASS |
| V. Observability & Debuggability | Structured logs per service; aggregate health; deploy/promotion audit incl. Release Set | PASS |
| VI. Simplicity & YAGNI | Managed multi-service runtimes over Kubernetes; static routing; one managed DB per env | PASS w/ justification |
| VII. Automated Quality Gates | CI runs fmt/validate/tflint/plan/smoke; fail-fast; no merge/promote on failure | PASS |

Initial Constitution Check: **PASS** (justified complexity: multi-service topology × 3 clouds).
Post-Design Check: **PASS** — the multi-service module contract and Release-Set pipeline keep
provider detail isolated and preserve isolation/rollback guarantees.

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-cloud-cicd/
├── plan.md  spec.md  research.md  data-model.md  quickstart.md
├── contracts/
│   ├── config-schema.md         # Release-set + env config (tfvars) contract
│   ├── module-interface.md      # Common per-cloud multi-service module inputs/outputs
│   └── pipeline-operations.md   # deploy / promote / teardown of a Release Set
└── checklists/requirements.md
```

### Source Code (repository root)

```text
infra/
└── terraform/
    ├── main.tf                 # selects the active per-cloud module by var.cloud_provider
    ├── variables.tf            # config contract incl. the service Release Set (map)
    ├── outputs.tf              # gateway_url, per-service refs, db ref, aggregate health target
    ├── versions.tf  backends/{aws,gcp,azure}.tfbackend
    ├── modules/
    │   ├── common/             # naming {client}-{env}[-service], tags, release-set validation
    │   ├── aws/                # VPC, ECS Fargate service per app, ALB (public), Cloud Map,
    │   │                       #   RDS PostgreSQL (private), S3, Secrets Manager, ACM
    │   ├── gcp/                # Cloud Run per service (+VPC connector, internal ingress),
    │   │                       #   Cloud SQL (private IP), GCS, Secret Manager, managed cert
    │   └── azure/              # Container Apps env (ext/internal ingress), Azure DB PostgreSQL,
    │                           #   Blob, Key Vault, managed cert
    └── environments/<client-name>/{stg,prod}.tfvars   # incl. release_set = { service = version }

scripts/
├── deploy.sh        # provision + deploy a Release Set to {client}-{env}; smoke test
├── promote.sh       # promote a STG-validated Release Set → PROD (gate); health-gated
├── teardown.sh      # destroy a {client}-{env}
└── validate-config.sh

tests/{contract,integration,fixtures}
.github/workflows/{deploy-stg.yml,promote-prod.yml,teardown.yml}
```

**Structure Decision**: Same provider-abstraction Terraform layout, but the per-cloud module now
provisions a **set of services** (iterating the Release Set) behind one public gateway with a
private network and shared managed DB. `deploy.sh`/`promote.sh` operate on the Release Set.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Multi-service topology (N services + private networking + public gateway) across three clouds | The app is microservices (features 002/003); each cloud expresses multi-service + private networking differently, so each module implements it behind one contract | Single-image deploy (previous plan) no longer matches the app. Kubernetes (EKS/GKE/AKS) would unify it but adds a heavy control plane per client — rejected for a solo maintainer; managed multi-service runtimes (ECS/Cloud Run/Container Apps) are lighter and sufficient. |
| Release Set as the deploy/promote unit | Services must be version-consistent across an environment (FR-022); promoting one image at a time risks incompatible mixes | Per-service independent promotion breaks the "validated together in STG" guarantee (SC-006) and complicates rollback. |
| Shared managed PostgreSQL per environment (schema/db per service) | Cost and ops for a solo maintainer; matches feature-003 assumption | A database per service multiplies managed-DB cost and backup surface with little benefit at this scale. |
