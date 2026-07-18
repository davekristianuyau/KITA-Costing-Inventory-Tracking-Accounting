# Implementation Plan: Containerized Database & Cache Runtime

**Branch**: `008-docker-cache-database` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-docker-cache-database/spec.md`

## Summary

Turn the spec-002 compose *skeleton* into a real, runnable data tier and full backend stack. Introduce a
containerized **cache** (Redis) alongside the existing **PostgreSQL** image, move each implemented
service's Flyway migrations from the shared `public` schema into its **own per-service schema** inside one
database, add the four missing service images, and health-gate a one-command `docker compose` bring-up of
the backend (gateway + the five implemented backend services + Postgres + Redis) with only the gateway
exposed. The cache is shared infrastructure; one concrete hot read path (operations-service catalog/item
reads) is implemented with `@Cacheable` + write-invalidation and graceful degradation to prove the tier.
Engine versions and config mechanism are pinned identically for local↔production parity.

**In scope**: the five *implemented* services — operations, hr, crm, procurement, workflow — plus the
gateway. **Excluded**: `reference-service` is still an empty spec-002 skeleton (no code, no migrations,
actuator disabled); it is left dormant in compose and wired in only when it is actually implemented.

## Technical Context

**Language/Version**: Java 17 (Spring Boot 3.5), Gradle 8.10.2 (existing backend).
**Primary Dependencies**: Spring Boot (web, data-jpa, actuator), Flyway, PostgreSQL driver; **new**:
`spring-boot-starter-data-redis` + Spring Cache (`@EnableCaching`, `RedisCacheManager`). Docker Compose v2.
**Storage**: PostgreSQL **16** (`postgres:16-alpine`, already used by scaffold + Testcontainers) — one
database `kita`, one schema per service. Cache: Redis **7.4** (`redis:7.4-alpine`), non-authoritative.
**Testing**: JUnit 5 + Testcontainers (Postgres already; add Redis Testcontainer for the cache path);
compose smoke test in quickstart; existing pure unit tests.
**Target Platform**: Linux containers on Docker (local dev + CI); production runs the same images or a
managed instance pinned to the same engine major/minor.
**Project Type**: Backend microservices + container orchestration (no application source-tree reshaping).
**Performance Goals**: Full healthy stack up in < 10 min from clean (SC-001); cached path ≥ 80% fewer DB
queries on repeat reads (SC-006).
**Constraints**: Only the gateway is host-exposed (FR-008); no secrets/defaults committed (FR-010);
data persists across restarts (FR-004); cache failure must not break correctness (FR-013).
**Scale/Scope**: 5 backend services + gateway + 2 datastores = 8 containers; single developer / SMB scale.

**Services & ports** (in scope): gateway 8081, operations-service 8083, hr-service 8085, crm-service
8086, procurement-service 8087, workflow-service 8088. (reference-service 8082 excluded — empty skeleton.)
**Schemas** (new): `operations`, `hr`, `crm`, `procurement`, `workflow`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ Spec + clarifications complete; this plan precedes code. |
| II. Test-Driven Development | ✅ Red-first: schema-isolation IT and cache hit/invalidation/degradation IT written before wiring; Money/existing suites untouched. |
| III. Security & Data Integrity | ✅ Committed `.env.example` only (real `.env` gitignored); remove `change-me` defaults; DB/Redis not host-exposed; migrations stay versioned; cache never authoritative; TLS in prod (parity). |
| IV. Environment Isolation | ✅ Compose is a dev/CI env; no shared datastore across envs; config env-scoped. |
| V. Observability & Debuggability | ✅ Actuator health on every service (enable on gateway + reference-service); Postgres/Redis healthchecks; structured startup-failure logs. |
| VI. Simplicity & YAGNI | ✅ One cache engine, one demonstrated cached path, reuse existing compose/Dockerfile patterns; no HA/replication. |
| VII. Automated Quality Gates | ✅ `:backend:build` gate unchanged; add a compose smoke check to CI (health-gated up → gateway probe → down). |

**Result**: PASS — no violations. Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/008-docker-cache-database/
├── plan.md              # This file
├── research.md          # Phase 0 — engine/version + schema/cache/secrets decisions
├── data-model.md        # Phase 1 — schema ownership, cache keyspaces, container/volume topology
├── quickstart.md        # Phase 1 — up/verify/teardown + parity check
├── contracts/           # Phase 1 — runtime/config + cache + health contracts
│   ├── runtime-config.md
│   ├── cache-contract.md
│   └── health-and-startup.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
docker-compose.yml                     # EXPAND: add redis + hr/crm/procurement/workflow, health-gate, gateway-only expose
docker-compose.override.yml.example    # NEW: optional dev-only host ports for DB/Redis (gitignored copy)
.env.example                           # NEW: documented env keys (no real secrets); real .env gitignored
Makefile  (or scripts/stack.sh)        # NEW: single up/down/clean commands

backend/
├── gateway/            # enable actuator dep + health; add routes for the 5 services
├── operations-service/ # schema=operations; add Redis + @Cacheable catalog demonstrator + invalidation
├── hr-service/         # NEW Dockerfile; schema=hr
├── crm-service/        # NEW Dockerfile; schema=crm
├── procurement-service/# NEW Dockerfile; schema=procurement
├── workflow-service/   # NEW Dockerfile; schema=workflow
├── reference-service/  # DORMANT (empty skeleton) — not wired in this feature
└── */src/main/resources/application.yml   # per-service: Flyway default-schema + currentSchema=<svc>,public
```

**Structure Decision**: No new application module. Changes are (a) orchestration at repo root
(`docker-compose.yml`, `.env.example`, run scripts), (b) four new service `Dockerfile`s (hr/crm/
procurement/workflow) mirroring the existing multi-stage pattern, (c) per-service `application.yml` schema
wiring for the five implemented services, and (d) a cache dependency + one `@Cacheable` demonstrator in
`operations-service` (catalog reads). Reuses the spec-002 skeleton shape.

## Phase 0 — Research (see research.md)

Resolved decisions: PostgreSQL 16 + Redis 7.4 pinning; **schema-per-service via
`?currentSchema=<svc>,public` + Flyway `default-schema`/`create-schemas`** (keeps `public` in the search
path so `gen_random_uuid()`/pgcrypto still resolves); Spring Cache over Redis with a non-fatal error
handler (graceful degradation); secrets via `.env`/secret store, no committed defaults; compose
health-gating with `depends_on: condition: service_healthy`; gateway-only host exposure; frontend and the
empty reference-service left out (deferred / dormant).

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: schema-ownership table (service→schema), container topology + volumes, cache
  keyspaces/TTLs, environment-config matrix.
- **contracts/runtime-config.md**: required env vars per container, exposure rules, teardown modes.
- **contracts/cache-contract.md**: key naming, TTL, invalidation triggers, degradation behavior.
- **contracts/health-and-startup.md**: per-container health probe + dependency-ordered startup contract.
- **quickstart.md**: one-command up, end-to-end gateway probe, restart-persistence check, parity check,
  clean teardown.

## Complexity Tracking

> No constitution violations — section intentionally empty.
