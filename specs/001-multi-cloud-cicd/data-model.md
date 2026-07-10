# Phase 1 Data Model: Multi-Cloud CI/CD Infrastructure Scaffolding (multi-service)

**Feature**: 001-multi-cloud-cicd | **Date**: 2026-07-10

The "data model" is the configuration/state model of the delivery system. Updated for the
multi-service application: an Environment now hosts a **set of Services** deployed as a coordinated
**Release Set** behind one public gateway.

## Entity: Deployment Target
| Field | Type | Rules |
|-------|------|-------|
| cloud_provider | enum `aws`\|`gcp`\|`azure` | required; rejected otherwise before provisioning (FR-007) |
| region | string | optional; defaults to the single system region; data stays in-region (FR-002a) |
| credentials_ref | reference | from CI secret store; never in repo (FR-013) |

## Entity: Client
| Field | Type | Rules |
|-------|------|-------|
| client_name | string | `^[a-z][a-z0-9-]{1,20}[a-z0-9]$`; immutable; the `{client-name}` token (FR-020) |
| custom_domain | string(FQDN) | optional per env; served at the public gateway with managed TLS (FR-001b) |

## Entity: Environment
| Field | Type | Rules |
|-------|------|-------|
| tier | enum `stg`\|`prod` | drives promotion gating + default sizing |
| name | derived | `{client_name}-{tier}`; per-resource suffixes add service name (FR-020) |
| region | string | inherited; data in-region |
| state_key | derived | `{client_name}-{tier}` — isolated remote state |
- Owns: one private network, the Service set, one managed DB, object storage, one public gateway.
  STG and PROD share **nothing** (network/DB/storage/services) — SC-005.

## Entity: Service
| Field | Type | Rules |
|-------|------|-------|
| name | string | e.g. `frontend`, `gateway`, `operations-service` |
| image | string | registry reference |
| version | string | immutable tag/digest (member of the Release Set) |
| visibility | enum `public`\|`private` | `frontend`+`gateway` public; backend services private (FR-001c) |
| size | enum/object | per-environment sizing (STG smaller than PROD, FR-008a) |
| health_path | path | per-service health; feeds aggregate health |
- Each Service → one compute workload on the cloud's multi-service runtime.

## Entity: Gateway / Ingress
- The single public entry point (cloud LB/ingress) terminating TLS for the custom domain, enforcing
  the app auth boundary, and routing to services. Exposes aggregate health (gateway + required
  backends) — SC-014.

## Entity: Release Set
| Field | Type | Rules |
|-------|------|-------|
| entries | map `{ service_name → version }` | the coordinated, version-consistent set (FR-022) |
| — | | the unit of deploy/promote/rollback; promoted STG→PROD unchanged (SC-006) |

## Entity: Promotion
- Deliberate move of a validated Release Set STG→PROD; blocked unless that exact set is healthy in
  STG (FR-010/011); never automatic (FR-009).

## Entity: Deployment Record
- Auditable entry: provider, environment, tier, **Release Set**, timestamp, outcome (FR-016).

## Entity: Configuration / Secrets Set
- Env- and provider-scoped settings + secrets, delivered per service; STG and PROD distinct; never
  in repo/logs (FR-013). Includes the `release_set` map and per-service config.

## Cross-entity invariants
- Naming: every resource derives from `{client-name}-{env}[-service]` (FR-020/021, SC-012).
- Isolation: no two Environments share network/DB/storage/services (SC-005/008).
- Public surface: only gateway/frontend reachable publicly; 0% of backend services public (SC-013).
- Health: success requires aggregate health; any required service unhealthy ⇒ unhealthy (SC-014).
- Idempotency: unchanged config ⇒ zero resource changes (FR-005, SC-003).
- Region containment: all data-bearing resources in the client's region (FR-002a).
