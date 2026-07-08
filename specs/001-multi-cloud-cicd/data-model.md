# Phase 1 Data Model: Multi-Cloud CI/CD Infrastructure Scaffolding

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

This feature's "data model" is the configuration and state model of the delivery system,
not application business data. Entities below are derived from the spec's Key Entities and
the clarifications. Each maps to Terraform variables, state, or CI inputs.

## Entity: Deployment Target

Where and how infrastructure is created.

| Field | Type | Rules |
|-------|------|-------|
| cloud_provider | enum `aws` \| `gcp` \| `azure` | Required. Rejects any other value before provisioning (FR-007). |
| region | string | Optional; defaults to the single system default region. If set, all data-bearing resources are confined to it (FR-002a). |
| credentials_ref | reference | Required at runtime; supplied by CI secret store, never in repo (FR-013). |

## Entity: Client

The customer a deployment belongs to.

| Field | Type | Rules |
|-------|------|-------|
| client_name | string | Required. Lowercase, `^[a-z][a-z0-9-]{1,20}[a-z0-9]$`, provider-safe. Immutable once provisioned. Used as the `{client-name}` token (FR-020). |
| custom_domain | string (FQDN) | Optional per environment; when set, the app is served under it with managed TLS (FR-001b). |

Relationship: one Client → exactly two Environments (STG and PROD).

## Entity: Environment

An isolated instance of the full stack at one tier.

| Field | Type | Rules |
|-------|------|-------|
| tier | enum `stg` \| `prod` | Required. Drives promotion gating and default sizing. |
| name | derived string | Computed as `{client_name}-{tier}` (e.g., `acme-stg`). Never hard-coded (FR-020). |
| size | enum/object | Per-environment sizing variable. Default: `stg` small, `prod` standard (FR-008a). Same architecture regardless of size. |
| region | string | Inherited from Deployment Target; data stays in-region. |
| state_key | derived string | Remote state key = `{client_name}-{tier}` — isolates state per environment (R5). |

Relationships: one Environment → one Application Stack; STG and PROD share **no** network,
database, or storage (FR-008, SC-005).

State/lifecycle: `absent → provisioning → healthy → (updating ↔ healthy) → destroying → absent`.
A failed `updating` transition auto-reverts to the previous `healthy` state (FR-006a).

## Entity: Application Stack

The deployable unit within an environment.

| Field | Type | Rules |
|-------|------|-------|
| app_image | string (repo) | Required. Cloud-agnostic registry reference. |
| app_version | string (immutable tag/digest) | Required. The exact artifact deployed; the promotion unit (R7). |
| compute | provider resource | ECS Fargate / Cloud Run / Container Apps (R2). Exposes a `/health` endpoint. |
| database | provider resource | Managed PostgreSQL, single node; PROD has PITR + daily backups (R3, FR-014a). |
| object_storage | provider resource | S3 / GCS / Blob; encrypted at rest (FR-014). |
| ingress | provider resource | Public HTTPS endpoint with managed TLS (FR-001, FR-001b). |
| secrets | provider resource | Per-environment secret store entries (FR-013). |

## Entity: Promotion

The deliberate move of a validated version from STG to PROD.

| Field | Type | Rules |
|-------|------|-------|
| client_name | string | Required; identifies the deployment. |
| app_version | string | Required; MUST already be deployed and healthy in `{client}-stg` (FR-011). |
| approver | identity | Required; recorded via CI environment approval (FR-009, FR-010). |
| source_tier / target_tier | `stg` / `prod` | Fixed direction stg → prod. |

Precondition (gate): promotion is **blocked** unless the same `app_version` is currently
healthy in STG (FR-011, SC-006). Never triggered automatically by a merge (FR-009, SC-007).

## Entity: Deployment Record

Auditable history of every deploy/promotion attempt (FR-016).

| Field | Type | Rules |
|-------|------|-------|
| timestamp | datetime | Set by the CI run (not by Terraform). |
| cloud_provider / client_name / tier | see above | Identifies the target. |
| operation | enum `deploy` \| `promote` \| `teardown` | Required. |
| app_version | string | The artifact involved. |
| outcome | enum `success` \| `failed` \| `rolled_back` | Required; failures include a reason. |
| actor | identity | Who triggered it. |

Storage: append-only; the CI run log + a persisted record (e.g., a JSON artifact or
audit object in each environment's storage) satisfy FR-016 without a dedicated database.

## Entity: Configuration / Secrets Set

Environment- and provider-scoped values that parameterize a deployment.

| Field | Type | Rules |
|-------|------|-------|
| non-secret config | tfvars file `environments/<client>/<tier>.tfvars` | Version-controlled (FR-019). Must satisfy the config-schema contract. |
| secret values | cloud secret store + CI secrets | Never in repo, logs, or plan output (FR-013, SC-010). STG and PROD secrets are distinct. |

## Cross-entity invariants

- **Naming**: every provisioned resource name/tag derives from `{client_name}-{tier}` (FR-020, FR-021, SC-012).
- **Isolation**: no two Environments (across tiers or clients) share network, DB, or storage (SC-005, SC-008).
- **Idempotency**: re-applying an unchanged Configuration Set yields zero resource changes (FR-005, SC-003).
- **Region containment**: all data-bearing resources for a Client reside in that Client's region (FR-002a).
