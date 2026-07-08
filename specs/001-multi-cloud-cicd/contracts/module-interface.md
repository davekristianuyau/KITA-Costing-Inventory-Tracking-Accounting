# Contract: Per-Cloud Module Interface

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-08

This is the heart of the provider-abstraction pattern (R1). The `aws`, `gcp`, and `azure`
modules under `infra/terraform/modules/` MUST each implement **exactly** these inputs and
outputs. Because the interface is identical, the root config swaps providers with a
config-only change (FR-003) and the pipeline/app never change. A contract test verifies all
three modules declare the same variable and output names.

## Module inputs (identical across all three modules)

| Input | Type | Meaning |
|-------|------|---------|
| `client_name` | string | `{client-name}` naming token (FR-020) |
| `env` | string | `stg` \| `prod` naming token (FR-008) |
| `region` | string | Target region; all data-bearing resources confined here (FR-002a) |
| `app_image` | string | Container image repository |
| `app_version` | string | Immutable image tag/digest to run (R7) |
| `size` | string | `small` \| `standard` \| `large` sizing profile (FR-008a) |
| `custom_domain` | string | Optional FQDN for public TLS ingress (FR-001b) |
| `db_backup_retention_days` | number | Backup retention; PITR implied for prod (FR-014a) |
| `tags` | map(string) | Tags/labels applied to every resource (FR-021) |

## Module outputs (identical across all three modules)

| Output | Type | Meaning |
|--------|------|---------|
| `app_url` | string | Public HTTPS URL of the deployed app (used by smoke test) |
| `health_check_url` | string | `/health` endpoint used to gate deploy + trigger rollback (FR-006a) |
| `db_connection_secret_ref` | string | Reference (not value) to the DB credential in the secret store (FR-013) |
| `object_storage_ref` | string | Identifier of the created object-storage bucket/container |
| `environment_name` | string | Resolved `{client_name}-{env}` (must equal the naming convention) |
| `resource_ids` | map(string) | Map of logical name → provider resource id, for audit/teardown verification |

## Behavioral guarantees each module MUST honor

1. **Naming**: every created resource's name/tag derives from `{client_name}-{env}`; no
   hard-coded names (FR-020, SC-012).
2. **Isolation**: creates its own network boundary, database, and object storage; shares
   none of them with any other module instance (FR-008, SC-005/SC-008).
3. **Encryption**: enables encryption at rest and TLS in transit wherever supported (FR-014).
4. **Idempotency**: no resource changes on re-apply with unchanged inputs (FR-005, SC-003).
5. **Health-gated rollout**: new `app_version` receives traffic only after `health_check_url`
   passes; otherwise the previous healthy version keeps serving (FR-006a).
6. **Region containment**: no data-bearing resource is created outside `region` (FR-002a).
7. **Clean destroy**: `terraform destroy` removes 100% of resources it created; `resource_ids`
   lets the pipeline verify nothing remains (FR-015, SC-009).

## Provider realization (informative — not part of the contract surface)

| Concern | aws | gcp | azure |
|---------|-----|-----|-------|
| Compute | ECS Fargate + ALB | Cloud Run | Container Apps |
| Database | RDS PostgreSQL | Cloud SQL PostgreSQL | Azure DB for PostgreSQL Flexible |
| Object storage | S3 | GCS | Blob Storage |
| Secrets | Secrets Manager | Secret Manager | Key Vault |
| TLS cert | ACM | Google-managed cert | Container Apps managed cert |
| Remote state | S3 + DynamoDB lock | GCS (versioned) | Azure Storage (blob lease) |
