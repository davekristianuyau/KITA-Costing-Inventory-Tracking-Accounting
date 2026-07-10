# backend/reference-service/

The **reference microservice** and the **template** every future KITA domain service
(costing, inventory, accounting) is copied from. Spring Boot + Spring Data JPA + Flyway,
backed by PostgreSQL, conforming to `../../contracts/openapi.yaml`.

**Status: scaffolding only.** Holds a module build skeleton, an `application.yml` (datasource +
Flyway wired to env), a `Dockerfile` skeleton, and placeholder source/resource/test directories.
The domain entity, endpoints, migrations, and tests are implemented in a later feature — see
[TEMPLATE.md](./TEMPLATE.md) for how to build on this layout.

Layout:
- `src/main/java/` — application source (placeholder).
- `src/main/resources/db/migration/` — Flyway migrations (placeholder; none yet).
- `src/test/java/` — unit + Testcontainers integration + contract tests (placeholder).
