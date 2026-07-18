# Phase 0 Research — Containerized Database & Cache Runtime

## D1. Database engine & version

- **Decision**: PostgreSQL **16** (`postgres:16-alpine`), one database `kita`.
- **Rationale**: Already used by the spec-002 skeleton and by every service's Testcontainers (`postgres:16-alpine`); matches "managed PostgreSQL per cloud". Pinning the same major/minor locally and in prod satisfies parity (FR-011, SC-004).
- **Alternatives**: Postgres 15 (older, no reason to downgrade); per-service DB containers (rejected — heavier, unlike the single managed prod instance, contradicts the clarified single-DB decision).

## D2. Cache engine & version

- **Decision**: Redis **7.4** (`redis:7.4-alpine`), used via Spring Data Redis + Spring Cache.
- **Rationale**: De-facto shared cache; first-class Spring Boot support (`spring-boot-starter-data-redis`, `RedisCacheManager`); already anticipated in code (workflow `PendingReviewStore` comment). Small footprint (alpine).
- **Alternatives**: In-process Caffeine (rejected — not shared across service containers, breaks "shared cache available to all services"); Memcached (rejected — weaker Spring integration, no persistence option).

## D3. Schema-per-service inside one database

- **Decision**: Each implemented service owns a schema (`operations`, `hr`, `crm`, `procurement`, `workflow`; reference-service excluded — empty skeleton). Wire per service:
  - JDBC URL: `...:5432/kita?currentSchema=<svc>,public`
  - `spring.flyway.default-schema: <svc>`, `spring.flyway.schemas: <svc>`, `spring.flyway.create-schemas: true`
  - `spring.jpa.properties.hibernate.default_schema: <svc>`
- **Rationale**: One database with cross-service visibility (clarified requirement) while each service writes only its own schema. Putting `public` **second** in `currentSchema` keeps shared extensions resolvable — critical because migrations use `gen_random_uuid()` (pgcrypto, installed in `public`). Flyway creates/targets the service schema; the Flyway history table lands per-schema so services never collide.
- **Gotcha resolved**: without `public` in the search path, `gen_random_uuid()` fails. A one-time `CREATE EXTENSION IF NOT EXISTS pgcrypto` (in `public`) is ensured at DB init (Postgres image `docker-entrypoint-initdb.d` script) so it exists before any service migrates.
- **Alternatives**: shared `public` for all (rejected — table-name collisions across services, no ownership); DB-per-service (rejected — see D1).

## D4. Cache usage pattern & graceful degradation

- **Decision**: Spring Cache abstraction (`@EnableCaching`, `@Cacheable`/`@CacheEvict`) backed by `RedisCacheManager`; register a `CacheErrorHandler` that logs and **swallows** Redis errors so a cache outage falls through to the database. Demonstrator: operations-service catalog/item reads (`@Cacheable` on the catalog read in `CatalogController`/its service, `@CacheEvict` on the corresponding catalog write).
- **Rationale**: Declarative, minimal code, correctness preserved when Redis is down (FR-013, SC-007). The product catalog is the highest read / lowest churn path in an implemented service — ideal first cache (SC-006). Pattern is reusable by any other service with a genuine need.
- **Alternatives**: manual `RedisTemplate` get/set (rejected — more code, easy to get invalidation wrong); read-through library (rejected — extra dependency, YAGNI).

## D5. Secrets & configuration

- **Decision**: Compose reads `.env` (gitignored); commit only `.env.example` with documented keys and non-secret placeholders. Remove the `change-me` fallback for DB password so a missing secret fails fast outside local dev. Prod supplies the same keys from its secret store (`{client}-{env}`), same mechanism.
- **Rationale**: Constitution III (no secrets in repo) + FR-010/FR-016; identical mechanism across environments (FR-012).
- **Alternatives**: hard-coded compose values (rejected — secrets in repo); Docker secrets files (deferred — overkill for local dev, revisit for prod hardening).

## D6. Orchestration, health-gating & exposure

- **Decision**: Extend the existing `docker-compose.yml` to 9 services. Healthchecks: Postgres `pg_isready`, Redis `redis-cli ping`, each JVM service `wget .../actuator/health`. `depends_on: { <dep>: { condition: service_healthy } }` orders startup (services→datastores healthy; gateway→services healthy). Publish **only** gateway `8081` to the host; Postgres/Redis/services stay on the internal `kita` network. Provide `docker-compose.override.yml.example` for opt-in dev host ports.
- **Rationale**: FR-003/007/008/015; deterministic, reproducible bring-up (SC-001/003/005).
- **Alternatives**: `depends_on` without health condition (rejected — races, services start before DB ready); Kubernetes/Compose profiles for everything (rejected — YAGNI for local dev).

## D7. Missing service images & actuator gaps

- **Decision**: Add multi-stage `Dockerfile`s for hr/crm/procurement/workflow mirroring the operations-service pattern (gradle build → temurin-jre-alpine, `EXPOSE <port>`, actuator healthcheck). Enable the (currently commented) actuator dependency on **gateway** and expose `health`. reference-service stays dormant (empty skeleton, actuator left disabled) until it is implemented.
- **Rationale**: Full-stack bring-up (FR-006) needs every *implemented* service imaged and health-probable (FR-015).
- **Alternatives**: run services via `bootRun` outside Docker (rejected — not the "full backend stack in Docker" the user chose).

## D8. Frontend

- **Decision**: Excluded from the default stack; not built/run by this feature. Gateway is the sole host-exposed service. (Existing frontend Dockerfile/compose entry left dormant behind a note.)
- **Rationale**: Clarified — frontend not yet specced/created; a later spec introduces it.
