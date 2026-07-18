---
description: "Task list for 008-docker-cache-database"
---

# Tasks: Containerized Database & Cache Runtime

**Input**: Design documents from `/specs/008-docker-cache-database/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD (Principle II, non-negotiable). Test/verification
tasks are written first within each story and MUST fail before the implementation tasks that follow.

**Organization**: Tasks grouped by user story. In scope: the five implemented services (operations, hr,
crm, procurement, workflow) + gateway. `reference-service` is a dormant empty skeleton — excluded.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- ⚠️ `docker-compose.yml` is a single shared file — tasks editing it are **not** mutually `[P]`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repo-root scaffolding for the containerized runtime.

- [X] T001 [P] Add ignore entries in `.gitignore` for `.env` and `docker-compose.override.yml` (keep `.env.example` and `docker-compose.override.yml.example` tracked)
- [X] T002 [P] Create `.env.example` at repo root documenting `POSTGRES_DB/USER/PASSWORD`, per-service `DATABASE_URL/USER/PASSWORD`, and `REDIS_HOST/PORT` with non-secret placeholders (per contracts/runtime-config.md)
- [X] T003 [P] Create `docker/postgres/initdb/01-extensions.sql` containing `CREATE EXTENSION IF NOT EXISTS pgcrypto;`
- [X] T004 [P] Create `docker-compose.override.yml.example` at repo root that publishes postgres `5432` and redis `6379` to the host for opt-in local dev only
- [X] T005 Create repo-root `Makefile` with `up` (`docker compose up -d --build`), `down` (`docker compose down`), `clean` (`docker compose down -v`), `ps`, and `logs` targets

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The data tier and schema-per-service wiring every story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Add a `redis` service (`redis:7.4-alpine`) to `docker-compose.yml` with a `redis-cli ping` healthcheck, the `kita-redisdata` volume, on the `kita` network, no host port
- [X] T007 Harden the `postgres` service in `docker-compose.yml`: pin `postgres:16-alpine`, add a `pg_isready` healthcheck, use the `kita-pgdata` named volume, mount `./docker/postgres/initdb` into `/docker-entrypoint-initdb.d`, and remove the host `5432` publish (moved to the override)
- [X] T008 Declare the `kita-pgdata` and `kita-redisdata` named volumes in the `volumes:` section of `docker-compose.yml`
- [X] T009 [P] Schema wiring in `backend/operations-service/src/main/resources/application.yml`: `DATABASE_URL` includes `?currentSchema=operations,public`; add `spring.flyway.default-schema=operations`, `spring.flyway.schemas=operations`, `spring.flyway.create-schemas=true`, `spring.jpa.properties.hibernate.default_schema=operations`
- [X] T010 [P] Schema wiring in `backend/hr-service/src/main/resources/application.yml` (schema `hr`, same keys as T009)
- [X] T011 [P] Schema wiring in `backend/crm-service/src/main/resources/application.yml` (schema `crm`)
- [X] T012 [P] Schema wiring in `backend/procurement-service/src/main/resources/application.yml` (schema `procurement`)
- [X] T013 [P] Schema wiring in `backend/workflow-service/src/main/resources/application.yml` (schema `workflow`)

**Checkpoint**: Postgres (with pgcrypto) + Redis defined and health-probable; every implemented service migrates into its own schema.

---

## Phase 3: User Story 1 - Real database & cache the backend runs against (Priority: P1) 🎯 MVP

**Goal**: A backend service (operations-service) runs against the containerized Postgres + Redis, applies its migrations into its own schema, persists data across restarts, and can reach the cache.

**Independent Test**: Bring up postgres + redis + operations-service; confirm healthy; write a record; `docker compose restart postgres`; confirm the record persists and the service reconnects; confirm the Redis health indicator is UP.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T014 [P] [US1] Schema-isolation integration test in `backend/operations-service/src/test/java/com/kita/operations/SchemaIsolationIT.java` (Testcontainers Postgres): assert operations tables are created in schema `operations` (not `public`) and the Flyway history table lives in `operations`

### Implementation for User Story 1

- [X] T015 [P] [US1] Add `spring-boot-starter-data-redis` to `backend/operations-service/build.gradle.kts`; add `REDIS_HOST`/`REDIS_PORT` (spring.data.redis.*) to `backend/operations-service/src/main/resources/application.yml`; leave the Redis health indicator enabled
- [X] T016 [US1] In `docker-compose.yml`, add an `/actuator/health` healthcheck to `operations-service` and `depends_on: { postgres: {condition: service_healthy}, redis: {condition: service_healthy} }`; confirm its `DATABASE_URL` carries `currentSchema=operations,public`
- [ ] T017 [US1] Add `scripts/verify-persistence.sh` that brings up postgres+redis+operations-service, writes a record via the API, runs `docker compose restart postgres`, re-reads, and asserts the record persists; reference it from quickstart.md §6

**Checkpoint**: MVP — a real service runs against real containerized datastores with persistence and cache connectivity.

---

## Phase 4: User Story 2 - One-command full backend stack (Priority: P2)

**Goal**: `make up` brings the gateway + all five implemented services + Postgres + Redis to healthy, health-gated, with only the gateway host-exposed.

**Independent Test**: From clean, run `make up`; all containers reach healthy; a request through `http://localhost:8081` returns a correct DB-backed response; Postgres/Redis/services are not reachable from the host; `make down` stops cleanly.

### Tests for User Story 2 ⚠️ (write first, must fail)

- [ ] T025 [P] [US2] Add `scripts/stack-smoke.sh` (executable spec of the story): `docker compose up -d --build`, wait for all services healthy, `curl` gateway `/actuator/health` and one routed endpoint, assert postgres/redis are refused from the host, then `docker compose down` — fails until T018–T024 land

### Implementation for User Story 2

- [ ] T018 [P] [US2] Create `backend/hr-service/Dockerfile` (multi-stage gradle→temurin-jre-alpine, `EXPOSE 8085`, actuator healthcheck) mirroring `backend/operations-service/Dockerfile`
- [ ] T019 [P] [US2] Create `backend/crm-service/Dockerfile` (`EXPOSE 8086`)
- [ ] T020 [P] [US2] Create `backend/procurement-service/Dockerfile` (`EXPOSE 8087`)
- [ ] T021 [P] [US2] Create `backend/workflow-service/Dockerfile` (`EXPOSE 8088`)
- [ ] T022 [US2] Enable actuator in `backend/gateway/build.gradle.kts` (uncomment the dependency), confirm `health` exposure in `backend/gateway/src/main/resources/application.yml`, and add an actuator healthcheck to `backend/gateway/Dockerfile`
- [ ] T023 [US2] Add `hr-service`, `crm-service`, `procurement-service`, `workflow-service` to `docker-compose.yml` (build context, `DATABASE_URL` with `currentSchema=<svc>,public`, `REDIS_*`, `/actuator/health` healthcheck, `depends_on: {postgres: service_healthy}`, `kita` network, no host port)
- [ ] T024 [US2] In `backend/gateway/src/main/resources/application.yml` add routes for operations/hr/crm/procurement/workflow; in `docker-compose.yml` make `gateway` `depends_on` all five services healthy, publish only `8081`, and keep `reference-service` + `frontend` out of the default stack (commented/dormant)

**Checkpoint**: The whole backend comes up with one command, health-gated, gateway-only exposed.

---

## Phase 5: User Story 3 - Local behaves like production (parity) (Priority: P2)

**Goal**: Pinned engine versions, migrations, and config mechanism are identical local↔prod; only env-scoped values differ; a restorable prod DB backup is required before reliance.

**Independent Test**: Run the parity check — engine tags pinned to the same major/minor and every service uses `currentSchema=<svc>,public`; switching `.env` between local and a prod-shaped file needs no code/engine/migration change.

### Tests for User Story 3 ⚠️ (write first, must fail)

- [ ] T026 [US3] Add `scripts/check-parity.sh` asserting `docker-compose.yml` pins `postgres:16-alpine` and `redis:7.4-alpine` and that every in-scope service config uses `currentSchema=<svc>,public`; exit non-zero on drift

### Implementation for User Story 3

- [ ] T027 [P] [US3] Create `docs/parity.md` documenting identical pinned image tags, identical `currentSchema` per service, env-scoped-values-only rule, and the managed-instance option pinned to the same engine major/minor
- [ ] T028 [US3] Document the production DB backup/restore requirement (restorable backup before reliance, FR-017) in `docs/parity.md`
- [ ] T029 [US3] Wire `scripts/check-parity.sh` into `.github/workflows/ci.yml` as a fast gate

**Checkpoint**: Drift between local and production is detected and blocked.

---

## Phase 6: User Story 4 - Shared cache, used where needed (Priority: P3)

**Goal**: The operations-service catalog read is served from Redis with correct write-invalidation and graceful degradation, proving the shared cache tier; the pattern is reusable by other services.

**Independent Test**: Read a catalog item twice → second read from cache (fewer DB queries); change it → next read reflects the change (no stale); stop Redis → read still returns the correct value from Postgres.

### Tests for User Story 4 ⚠️ (write first, must fail)

- [ ] T030 [US4] Cache integration test in `backend/operations-service/src/test/java/com/kita/operations/catalog/CatalogCacheIT.java` (Testcontainers Postgres + Redis): assert (a) repeat read hits cache / ≥80% fewer DB queries, (b) write → read returns new value (no stale), (c) with Redis stopped the read still returns the correct value from the DB

### Implementation for User Story 4

- [ ] T031 [US4] Add `@EnableCaching` + `RedisCacheManager` + a swallow-and-log `CacheErrorHandler` in `backend/operations-service/src/main/java/com/kita/operations/config/CacheConfig.java`
- [ ] T032 [US4] Annotate the catalog read with `@Cacheable("catalog:item")` and the catalog write with `@CacheEvict` in the catalog service backing `backend/operations-service/src/main/java/com/kita/operations/api/CatalogController.java`
- [ ] T033 [US4] Make `CatalogCacheIT` pass; verify the degradation path (Redis down → DB fallback) per contracts/cache-contract.md

**Checkpoint**: One real cached read path proven end-to-end; cache never authoritative.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T034 [P] Update root `README.md` and `specs/008-docker-cache-database/quickstart.md` with the one-command stack workflow (`make up`/`down`/`clean`)
- [ ] T035 Run `cd backend && ./gradlew build` to confirm the schema + Redis config changes compile and all existing suites still pass
- [ ] T036 [P] Secret-hygiene check: grep the repo for committed credentials, confirm `.env` is gitignored and only `.env.example` is tracked (Constitution III, FR-010)
- [ ] T037 Run `specs/008-docker-cache-database/quickstart.md` end-to-end and confirm SC-001..SC-008

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: after Setup — BLOCKS all user stories.
- **US1 (Phase 3)**: after Foundational. MVP.
- **US2 (Phase 4)**: after Foundational (independent of US1; shares `docker-compose.yml`, so compose edits serialize).
- **US3 (Phase 5)**: after Foundational; best after US2 (parity check reads the full compose + all service configs).
- **US4 (Phase 6)**: after US1 (needs the operations-service Redis dependency from T015) + Foundational.
- **Polish (Phase 7)**: after all desired stories.

### Within Each User Story

- The test/verification task is written first and must fail before its implementation tasks.
- Config/build changes precede compose wiring; compose wiring precedes smoke/e2e.

### Parallel Opportunities

- Setup: T001, T002, T003, T004 in parallel.
- Foundational: T009–T013 (per-service `application.yml`, different files) in parallel; T006/T007/T008 serialize (same `docker-compose.yml`).
- US2: T018–T021 (four new Dockerfiles) in parallel; T023/T024 serialize on `docker-compose.yml`.
- US3: T027 parallel with the script/CI tasks.

---

## Parallel Example: Foundational schema wiring

```bash
# Different files — safe to do together:
Task T009: operations-service application.yml → schema operations
Task T010: hr-service application.yml → schema hr
Task T011: crm-service application.yml → schema crm
Task T012: procurement-service application.yml → schema procurement
Task T013: workflow-service application.yml → schema workflow
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE**: real service on real containerized DB + cache, data persists.

### Incremental Delivery

Setup + Foundational → US1 (MVP) → US2 (full one-command stack) → US3 (parity gate) → US4 (cache demonstrator). Each story is independently testable and adds value without breaking the previous.

---

## Notes

- Only the gateway is host-exposed; DB/Redis/services stay on the `kita` network (opt-in host ports via the override file).
- Testcontainers ITs (T014, T030) run in CI (Linux) or locally with Docker exposed on TCP; the `scripts/*.sh` smoke/parity/persistence checks drive the real compose stack.
- reference-service and the frontend are intentionally out of this feature (dormant / deferred spec).
- Commit after each task or logical group; simple messages, no AI attribution.
