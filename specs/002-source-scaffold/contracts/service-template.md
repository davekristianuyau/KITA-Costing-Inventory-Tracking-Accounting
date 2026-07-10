# Contract: Add-a-Microservice Pattern

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

The documented, repeatable pattern for adding a new backend domain microservice without
modifying existing services (FR-008, SC-005). The `reference-service` module is the template.

## Steps to add a service `foo`

1. **Copy the module**: duplicate `backend/reference-service/` to `backend/foo-service/`;
   register it in `backend/settings.gradle.kts`. It inherits shared Spotless/Checkstyle/test
   config from the root build.
2. **Define the contract first**: add the service's paths/schemas to `contracts/openapi.yaml`
   (or a referenced file), bumping `info.version`. This is the source of truth.
3. **Migrations**: add `db/migration/V1__create_foo.sql` under the new module (its own schema
   or table set; shared per-environment database per feature 001 assumptions).
4. **Implement**: controller → service (`@Transactional`) → JPA entity/repository, conforming
   to the OpenAPI schemas; Bean Validation at the boundary.
5. **Route it**: add one gateway Route (`id`, path predicate `/api/foo/**`, target
   `${FOO_SERVICE_URL}`) in the gateway `application.yml` — no change to other services' code.
6. **Wire runtime**: add the service (and its `FOO_SERVICE_URL`) to `docker-compose.yml` and
   the Release Set manifest; add a `Dockerfile` (copy the reference one).
7. **Tests first**: add contract tests (OpenAPI conformance), Testcontainers integration +
   migration tests, and unit tests — written before the implementation (constitution II).

## Invariants the pattern preserves

- Adding a service touches only: the new module, `contracts/openapi.yaml`, one gateway Route,
  `docker-compose.yml`/Release Set. It MUST NOT modify existing services' source (SC-005).
- The new service gets its own image, private placement, `/health`, structured logs, and
  externalized config — automatically satisfying the container contract.
- The contract is updated before the code, keeping OpenAPI the source of truth.

## Acceptance (verifies FR-008 / SC-005)

Add a throwaway `ping-service` following the steps; confirm it builds into its own image,
routes through the gateway at `/api/ping/**`, passes its contract test, and that no existing
service's source files changed (verified by diff).
