# Phase 1 Data Model: Application Source Code Scaffolding

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

This feature's data model has two parts: (1) the **runtime persistence model** the reference
service establishes (the pattern future domains follow), and (2) the **structural entities**
of the scaffold itself (services, images, contract). Business domain data
(costing/inventory/accounting) is out of scope.

## Part 1 — Runtime persistence (reference service)

### Entity: SampleItem (reference domain object)

A deliberately generic entity that exercises the full persistence path. Future domain services
replace it with real entities following the same shape.

| Field | Type | Rules |
|-------|------|-------|
| id | UUID | Primary key; server-generated. |
| name | string (1..120) | Required; Bean Validation `@NotBlank`, length-bounded. |
| quantity | integer | Required; `>= 0`. |
| created_at | timestamp (UTC) | Set on insert; not user-writable. |
| updated_at | timestamp (UTC) | Set on insert/update. |

- **Persistence**: Spring Data JPA entity + repository; writes wrapped in `@Transactional`.
- **Migration**: created by Flyway `V1__create_sample_table.sql` (forward-only, repeatable to
  apply on a clean DB). Re-running migrations on an up-to-date DB is a no-op (SC-009).
- **Monetary note**: SampleItem has no money field; when real domains add monetary values they
  MUST use exact decimal types (`BigDecimal` / `NUMERIC`), per constitution III.

### State / lifecycle

`SampleItem`: `created → (updated)* → (deleted)`. The reference slice demonstrates create + read
(and optionally update) to prove the round trip; delete is available but not required for the
acceptance round trip.

## Part 2 — Scaffold structural entities

These mirror the spec's Key Entities and map to concrete build/runtime artifacts.

### Entity: Service

| Field | Type | Rules |
|-------|------|-------|
| name | string | One of `frontend`, `gateway`, `reference-service` at scaffold stage. |
| image | reference | One container image per service (FR-005); immutable version tag (FR-006). |
| port | integer | Documented; exposed for its role (frontend/gateway public; service private). |
| health_endpoint | path | `/health` (Actuator for JVM services; Nginx status for frontend). |
| visibility | enum `public` \| `private` | `frontend`+`gateway` public; backend services private. |

### Entity: API Gateway (Spring Cloud Gateway)

- Holds the **Route** set: `{ id, path-predicate, target-service-uri, filters }`, configured in
  `application.yml`, env-overridable. Adding a service adds a Route (FR-008).
- Exposes aggregate health derived from downstream service health.

### Entity: Edge (Nginx)

- Serves the built frontend static assets; applies cache-control + gzip; proxies `/api/*` to
  the API Gateway. Config in `nginx.conf`. Packaged as the frontend image.

### Entity: Interface Contract (OpenAPI)

- `contracts/openapi.yaml`, versioned (`info.version`). Source of truth for the reference
  endpoint's request/response schemas. Generates the frontend TS client; validated by backend
  contract tests (FR-009/009a).

### Entity: Release Set

- The coordinated map `{ service-name → image-version }` known compatible and deployed
  together. Expressed as a versioned manifest (e.g., a `release-set.yaml` / Compose `.env`),
  consumed by feature 001 as the promotion/deploy unit (FR-016).

### Entity: Schema Migration

- A Flyway versioned SQL file under `reference-service/.../db/migration`. Ordered, immutable
  once applied; the pattern every future service reuses.

## Cross-entity invariants

- **Contract-first**: the frontend client and backend responses both conform to
  `contracts/openapi.yaml`; a mismatch fails contract tests (FR-009a).
- **One image per service**: each Service maps to exactly one image; no service shares another's
  image (FR-005).
- **Externalized config**: no Service hard-codes DB URLs, credentials, ports, or peer URLs;
  all come from environment (FR-014).
- **Private backend**: only `frontend` (Nginx) and `gateway` are reachable from outside the
  Compose network; `reference-service` and `postgres` are internal.
- **Migration safety**: applying migrations to a clean or up-to-date database is idempotent and
  non-destructive (SC-009).
