# Contract: Per-Cloud Module Interface (multi-service)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

The `aws`, `gcp`, and `azure` modules MUST each implement exactly these inputs/outputs so the root
config swaps providers with config only (FR-003) and the pipeline never changes. Updated to
provision a **set of services** behind one public gateway. A contract test verifies all three
modules declare the same names.

## Module inputs (identical across modules)
| Input | Type | Meaning |
|-------|------|---------|
| `client_name` | string | `{client-name}` naming token |
| `env` | string | `stg`\|`prod` naming token |
| `region` | string | data-bearing resources confined here (FR-002a) |
| `release_set` | map(object) | `service_name → { image, version, visibility, port, health_path }` (FR-022) |
| `size` | string | sizing profile (FR-008a) |
| `custom_domain` | string | optional FQDN for the public gateway (FR-001b) |
| `db_backup_retention_days` | number | PITR/backups for prod (FR-014a) |
| `tags` | map(string) | applied to every resource (FR-021) |

## Module outputs (identical across modules)
| Output | Type | Meaning |
|--------|------|---------|
| `gateway_url` | string | public HTTPS URL of the gateway (single entry point) |
| `aggregate_health_url` | string | endpoint reflecting gateway + required-service health (SC-014, FR-006a) |
| `service_endpoints` | map(string) | `service_name → internal URL` (backend private) |
| `db_connection_secret_ref` | string | reference (not value) to DB creds in the secret store (FR-013) |
| `object_storage_ref` | string | created bucket/container id |
| `environment_name` | string | resolved `{client_name}-{env}` |
| `resource_ids` | map(string) | logical name → provider resource id (audit/teardown) |

## Behavioral guarantees each module MUST honor
1. Provision one compute workload **per service** in `release_set`, on the cloud's managed
   multi-service runtime, wired to the shared managed PostgreSQL and object storage.
2. Expose **only** `public` services (gateway/frontend) via the public LB/ingress with managed TLS;
   `private` services get no public ingress (FR-001c, SC-013).
3. Route service-to-service and service-to-DB traffic over the **private network** only (FR-004a).
4. Name/tag every resource from `{client-name}-{env}[-service]` (FR-020/021, SC-012).
5. Isolation: own network, DB, storage, and service workloads — shared with no other env (SC-005/008).
6. Encryption at rest + TLS in transit (FR-014); PROD DB PITR + daily backups (FR-014a).
7. Idempotent: no changes on re-apply with unchanged inputs (FR-005, SC-003).
8. Health-gated rollout per service; report success only when **aggregate health** is healthy;
   otherwise auto-rollback to the previous Release Set (FR-006a, SC-014).
9. Region containment (FR-002a); clean `destroy` removes 100% of resources (FR-015, SC-009).

## Provider realization (informative)
| Concern | aws | gcp | azure |
|---------|-----|-----|-------|
| Per-service compute | ECS Fargate service | Cloud Run service | Container Apps app |
| Public ingress | ALB (host/path) | Cloud Run external | Container Apps external ingress |
| Private services | private subnets + Cloud Map | internal ingress + VPC connector | internal ingress |
| Database | RDS PostgreSQL (private) | Cloud SQL (private IP) | Azure DB PostgreSQL (private) |
| Secrets / TLS / state | Secrets Mgr / ACM / S3+DDB | Secret Mgr / managed cert / GCS | Key Vault / managed cert / Storage |
