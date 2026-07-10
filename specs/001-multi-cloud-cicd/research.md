# Phase 0 Research: Multi-Cloud CI/CD Infrastructure Scaffolding (multi-service)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

Resolves the technical approach now that KITA deploys as a **set of services** (features 002/003)
rather than a single image. Prior single-image decisions are superseded where noted. Framework
families (Terraform, the three clouds) and behavior (STG/PROD, gated promotion, single region,
lot/PITR) remain as previously clarified.

## R1. IaC structure (unchanged)

- **Decision**: One Terraform codebase; root selects a per-cloud module by `var.cloud_provider`;
  all modules implement one contract (contracts/module-interface.md); shared naming/tags/validation
  in `modules/common`.
- **Rationale**: Config-only provider switch (FR-003) with auditable "minimal changes".

## R2. Multi-service compute per cloud (supersedes single-image R2)

- **Decision**: Managed multi-service runtimes, one deployable per service in the Release Set:
  - **AWS**: an **ECS Fargate service per app** in a VPC; a single public **ALB** routes to the
    frontend + gateway (host/path rules); backend services run in private subnets and are reached
    via **Cloud Map** service discovery / internal target groups.
  - **GCP**: a **Cloud Run service per app**; frontend/gateway have external ingress, backend
    services use **internal ingress** reachable via a **Serverless VPC connector**.
  - **Azure**: one **Container Apps environment** with an app per service; frontend/gateway use
    external ingress, backend apps use internal ingress; built-in service discovery by app name.
- **Rationale**: Serverless-container runtimes give per-service scaling, private/public ingress,
  revisions, and health-gated rollout without managing a cluster (Simplicity).
- **Alternatives**: Kubernetes (EKS/GKE/AKS) — rejected (control-plane + ops weight per client);
  VMs — rejected (patching/supervision toil).

## R3. Network & exposure

- **Decision**: Private network per environment (VPC / VPC + connector / Container Apps env). Only
  the **gateway (and frontend)** are publicly reachable via the cloud LB/ingress with managed TLS
  and the client's custom domain; **backend services are private** (FR-001c). Service-to-service
  and service-to-DB traffic stays on the private network (FR-004a).
- **Rationale**: Least exposure for a financial system; matches feature-003 gateway topology.

## R4. Service discovery & routing

- **Decision**: The gateway addresses backend services by stable internal names, injected as env
  config (e.g., `OPERATIONS_SERVICE_URL`): AWS Cloud Map DNS, GCP internal Cloud Run URLs, Azure
  Container Apps app names. Static configuration (no discovery server), per feature 003.
- **Alternatives**: Eureka/Consul — rejected (extra moving part; YAGNI at this scale).

## R5. Shared managed PostgreSQL (unchanged intent)

- **Decision**: One managed PostgreSQL per environment (private), shared by services with schema/db
  separation. PROD: daily backups + PITR; STG relaxed. Business logic lives in services.
- **Alternatives**: DB per service — rejected (cost/backup surface at this scale).

## R6. Release Set as the unit of deploy/promote (new)

- **Decision**: A **Release Set** is a versioned map `{ service-name → immutable image tag/digest }`
  expressed in each environment's tfvars (`release_set`). deploy/promote/rollback operate on the
  whole set atomically; promotion carries the identical set validated in STG (FR-022, SC-006).
- **Rationale**: Guarantees version-consistent environments and coherent rollback.
- **Alternatives**: Per-service promotion — rejected (incompatible-version risk).

## R7. Health gating & auto-rollback (extended to aggregate)

- **Decision**: Deployment success requires **aggregate health** — the gateway healthy AND all
  required backend services healthy (SC-014). On failure, auto-rollback the environment to the
  previous Release Set (per-service revision rollback on each runtime) so the last healthy set keeps
  serving (FR-006a); failed versions get no traffic.
- **Alternatives**: Per-service health without an aggregate gate — rejected (partial/inconsistent
  deploys reported as success).

## R8. Remote state, region, CI, artifacts, secrets — carried over

- **State**: per cloud, locked, keyed `{client}-{env}`.
- **Region**: single default, per-client override, in-region data (FR-002a).
- **CI/CD**: GitHub Actions; `deploy-stg` (auto on merge), `promote-prod` (manual, protected
  Environment approval → gate), `teardown` (manual). Logic in provider-agnostic scripts.
- **Artifacts**: all service images pulled from a registry by the Release Set; promotion reuses the
  same tags/digests (no rebuild).
- **Secrets**: cloud secret managers, per env and per service; never in repo/logs.

## Deferred (not blocking)

- Multi-region HA, per-service databases, canary/blue-green beyond the runtime's native revision
  rollback, and a self-service cloud-choice UI.
