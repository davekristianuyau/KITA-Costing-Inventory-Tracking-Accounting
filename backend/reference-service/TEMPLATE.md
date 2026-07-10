# Service Template

`reference-service/` is the copyable template for adding a new KITA domain microservice.
See the full pattern in [docs/add-a-service.md](../../docs/add-a-service.md). In brief:

1. Copy this directory to `backend/<name>-service/` and register it in
   `backend/settings.gradle.kts` (`include(":<name>-service")`).
2. Add the service's paths/schemas to `contracts/openapi.yaml` (contract-first).
3. Add Flyway migrations under `src/main/resources/db/migration/` (e.g., `V1__create_<name>.sql`).
4. Implement controller → `@Transactional` service → JPA entity/repository conforming to the contract.
5. Add one gateway route (`/api/<name>/**`) in `backend/gateway` — no changes to other services.
6. Add the service (and its `*_SERVICE_URL`) to `docker-compose.yml` and the release-set manifest.
7. Write tests first (contract, Testcontainers integration, unit) — TDD.

Adding a service MUST NOT modify existing services' source.
