# Adding a New Microservice

KITA backend services are added by copying the `backend/reference-service/` template. This keeps
every domain (costing, inventory, accounting, …) independently developable and deployable, and
routed through the gateway — without modifying existing services.

> Scaffolding note: the reference-service is currently a skeleton. Once it is implemented, this
> pattern is fully exercisable; the steps below already define the convention.

## Steps to add a service `foo`

1. **Copy the template**: `backend/reference-service/` → `backend/foo-service/`; register it in
   `backend/settings.gradle.kts` with `include(":foo-service")`.
2. **Contract first**: add `foo`'s paths and schemas to `contracts/openapi.yaml`, bumping
   `info.version`. The contract is the source of truth.
3. **Migrations**: add `src/main/resources/db/migration/V1__create_foo.sql` in the new module.
4. **Implement**: controller → `@Transactional` service → JPA entity/repository conforming to the
   contract, with Bean Validation at the boundary.
5. **Route it**: add one gateway route (`/api/foo/**` → `${FOO_SERVICE_URL}`) in
   `backend/gateway/src/main/resources/application.yml`. Do not touch other services.
6. **Wire runtime**: add the service (and `FOO_SERVICE_URL`) to `docker-compose.yml`, the
   `.env.example`, and the release-set manifest; add its `Dockerfile` (copy the template's).
7. **Tests first (TDD)**: write contract tests (OpenAPI conformance), Testcontainers integration
   + migration tests, and unit tests before the implementation.

## Invariants

- Adding a service touches only: the new module, `contracts/openapi.yaml`, one gateway route,
  and `docker-compose.yml`/release-set. It MUST NOT modify existing services' source.
- The new service automatically inherits the container contract (own image, private placement,
  `/health`, structured logs, env-only config).
