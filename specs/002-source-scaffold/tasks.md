---

description: "Task list for Application Source Code Scaffolding"
---

# Tasks: Application Source Code Scaffolding

**Input**: Design documents from `/specs/002-source-scaffold/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED. Constitution Principle II (TDD, NON-NEGOTIABLE) and the plan's Testing
section require contract tests (OpenAPI), migration/integration tests (Testcontainers), and
frontend component tests — written first and failing before implementation.

**Organization**: Grouped by user story. US1/US2 bring the stack up and formalize images
(minimal services); US3/US4 flesh out the reference microservice and frontend; US5 hardens
quality gates. Stack: React+TS+Vite (Nginx), Spring Boot 3.3 / Java 21 (Gradle multi-module),
Spring Cloud Gateway, PostgreSQL+Flyway, OpenAPI contract-first, Docker Compose.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- Paths are repository-relative per plan.md.

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Create repo structure per plan.md: `frontend/`, `backend/`, `contracts/`, `docs/`, plus placeholder `docker-compose.yml` and `Makefile`
- [ ] T002 [P] Initialize backend Gradle multi-module in `backend/`: `settings.gradle.kts` (`:gateway`, `:reference-service`) and root `build.gradle.kts` (Java 21, Spring Boot 3.3 BOM, Spring Cloud 2023.x BOM, Spotless google-java-format, Checkstyle)
- [ ] T003 [P] Initialize frontend in `frontend/`: Vite + React 18 + TypeScript, React Router, ESLint + Prettier configs
- [ ] T004 [P] Add the source-of-truth API contract at `contracts/openapi.yaml` (from specs/002 contracts)
- [ ] T005 [P] Create `Makefile` with `build`, `up`, `test`, `lint` targets (skeletons delegating to gradle/npm/compose)

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: Complete before any user story.

- [ ] T006 Author `docker-compose.yml` base: PostgreSQL 16 service (env-configured, healthcheck), a shared network, and service placeholders with `depends_on`/healthchecks
- [ ] T007 [P] Shared structured JSON logging (Logback encoder) as a reusable resource under `backend/` applied by both services
- [ ] T008 [P] Backend test base: JUnit 5 config + Testcontainers PostgreSQL base class in `backend/reference-service/src/test/java/.../support/`
- [ ] T009 [P] Frontend test setup: Vitest + React Testing Library config in `frontend/`
- [ ] T010 [P] Wire OpenAPI TS client generation (openapi-typescript) from `contracts/openapi.yaml` into `frontend/src/api/` (npm script)
- [ ] T011 [P] Externalized config: `.env.example` + Compose env wiring for DB creds, `GATEWAY_URL`, `REFERENCE_SERVICE_URL` (no hard-coded values) per FR-014

**Checkpoint**: Repo builds, Postgres + network defined, contract + test harness ready.

---

## Phase 3: User Story 1 - Build and run all services from a clean checkout (Priority: P1) 🎯 MVP

**Goal**: One command builds everything; one command runs the full stack; all services report
healthy and the frontend is reachable showing a healthy backend.

**Independent Test**: On a clean checkout, `make build && make up`; confirm gateway aggregate
`/health` is UP, each service `/health` is UP, and http://localhost:8080 renders.

### Tests for User Story 1 (write first, must FAIL) ⚠️

- [ ] T012 [P] [US1] Smoke test `tests/integration/smoke_healthy.sh` asserting, after `make up`, the gateway aggregate `/health` = UP, each service `/health` = UP, and the frontend responds 200
- [ ] T013 [P] [US1] Test `tests/integration/idempotent_up.sh` asserting a second `make up` on a running stack makes no destructive changes

### Implementation for User Story 1

- [ ] T014 [P] [US1] Minimal `reference-service` Spring Boot app with Actuator `/health` + `/info` (version), no domain yet, in `backend/reference-service/`
- [ ] T015 [P] [US1] Minimal `gateway` (Spring Cloud Gateway) with Actuator health and an aggregate-health indicator over downstream services in `backend/gateway/`
- [ ] T016 [P] [US1] Minimal React shell build + `frontend/nginx.conf` serving static assets and proxying `/api/*` to the gateway
- [ ] T017 [P] [US1] Per-service `Dockerfile`s (multi-stage): `backend/gateway/Dockerfile`, `backend/reference-service/Dockerfile`, `frontend/Dockerfile` (node build → nginx)
- [ ] T018 [US1] Wire all services into `docker-compose.yml` (build contexts, ports, env, healthchecks, network) and implement `make build`/`make up` so the stack comes up healthy

**Checkpoint**: Full stack boots healthy from two commands. **MVP reached.**

---

## Phase 4: User Story 2 - Each service builds into its own container image (Priority: P1)

**Goal**: One independently-runnable image per service, each with an immutable version tag,
documented port + health + env-only config; the set is expressed as a Release Set.

**Independent Test**: Build each image separately, run it alone, confirm it exposes its health
endpoint and port and carries an immutable tag; only frontend + gateway publish ports.

### Tests for User Story 2 (write first, must FAIL) ⚠️

- [ ] T019 [P] [US2] Test `tests/contract/image_contract.sh` asserting each image builds standalone, carries an immutable version tag/label, and exposes the documented port + health endpoint (per contracts/container-contract.md)
- [ ] T020 [P] [US2] Test `tests/contract/port_exposure.sh` asserting only `frontend` and `gateway` publish host ports; `reference-service` and `postgres` are internal-only

### Implementation for User Story 2

- [ ] T021 [P] [US2] Harden each `Dockerfile` for standalone build, env-only config, structured stdout logs, and a documented `EXPOSE` port (FR-005/006, FR-014)
- [ ] T022 [US2] Add Release Set manifest `release-set.yaml` (+ Compose `.env`) mapping `service → immutable version`, documented in `docs/release-set.md` (FR-016)
- [ ] T023 [US2] Inject immutable version tag at build time (build arg → image label) and surface it via each service `/info`

**Checkpoint**: Every service is an independently deployable, tagged image feeding feature 001.

---

## Phase 5: User Story 3 - Backend split into independent microservices (Priority: P2)

**Goal**: The reference microservice is a full persistence-backed slice conforming to the
OpenAPI contract, routed through the gateway, with a documented add-a-service pattern.

**Independent Test**: Call `/api/reference/items` through the gateway → get DB-backed data;
add a throwaway service via the pattern and confirm it routes without touching existing code.

### Tests for User Story 3 (write first, must FAIL) ⚠️

- [ ] T024 [P] [US3] Contract test `backend/reference-service/src/test/.../ReferenceContractTest` validating responses conform to `contracts/openapi.yaml` (OpenAPI validator)
- [ ] T025 [P] [US3] Testcontainers integration test asserting Flyway creates the sample table and a POST then GET persists/reads a `SampleItem`
- [ ] T026 [P] [US3] Unit test for `SampleItem` validation (name 1..120, quantity ≥ 0) and Problem responses on invalid input
- [ ] T027 [P] [US3] Migration idempotency test asserting re-running migrations on an up-to-date DB is a no-op (SC-009)

### Implementation for User Story 3

- [ ] T028 [P] [US3] Flyway `backend/reference-service/src/main/resources/db/migration/V1__create_sample_table.sql`
- [ ] T029 [P] [US3] `SampleItem` JPA entity + Spring Data repository in `backend/reference-service/src/main/java/.../reference/`
- [ ] T030 [US3] Reference controller (`GET/POST /reference/items`, `GET /reference/items/{id}`) + `@Transactional` service + Bean Validation + RFC-9457 Problem handler, conforming to the contract (makes T024–T026 pass)
- [ ] T031 [US3] Gateway route `/api/reference/**` → `reference-service` (static, env-overridable) in `backend/gateway/src/main/resources/application.yml`
- [ ] T032 [US3] Write `docs/add-a-service.md` from contracts/service-template.md and verify by adding a throwaway `ping-service` (routes at `/api/ping/**`, no existing source changed), then remove it

**Checkpoint**: Reference microservice is a real, contract-conformant, DB-backed vertical slice.

---

## Phase 6: User Story 4 - Frontend application shell with backend integration (Priority: P2)

**Goal**: The React app shows an application shell and a view that displays DB-backed data
retrieved from the reference service through the gateway, with a clear error state.

**Independent Test**: Load the frontend, open the reference view → data from the backend
appears; stop the backend → a clear error state shows (no blank/crash).

### Tests for User Story 4 (write first, must FAIL) ⚠️

- [ ] T033 [P] [US4] Component test `frontend/tests/reference-view.test.tsx` rendering items from a mocked generated client
- [ ] T034 [P] [US4] Component test asserting a clear error/empty state when the API call fails (FR-013, SC-007)

### Implementation for User Story 4

- [ ] T035 [P] [US4] Generate and wire the OpenAPI TS client into `frontend/src/api/` and a typed fetch wrapper
- [ ] T036 [P] [US4] App shell (routing, layout, nav, landing view) in `frontend/src/app/`
- [ ] T037 [US4] Reference feature view in `frontend/src/features/reference/`: list + create form consuming `/api/reference/items`, with loading/error/empty states (makes T033/T034 pass)
- [ ] T038 [US4] Finalize `frontend/nginx.conf`: cache-control + gzip for static assets and `/api/*` proxy to the gateway (FR-017a)

**Checkpoint**: Full UI → gateway → service → DB round trip works and degrades gracefully.

---

## Phase 7: User Story 5 - Test and quality-gate scaffolding (Priority: P3)

**Goal**: One documented command runs all tests; one runs lint/format; both fail on violations
— establishing the constitution's quality gates from the first commit.

**Independent Test**: `make test` runs frontend + backend + contract tests green; `make lint`
reports compliance and fails on a deliberately malformed sample.

### Tests for User Story 5 (write first, must FAIL) ⚠️

- [ ] T039 [P] [US5] Add a deliberately malformed sample (one per side) and a test asserting `make lint` fails on it, then correct it so lint passes

### Implementation for User Story 5

- [ ] T040 [US5] Finalize `make test` aggregating frontend Vitest + backend JUnit/Testcontainers + OpenAPI contract tests, with at least one passing test per service and the frontend (SC-006)
- [ ] T041 [US5] Finalize `make lint` (ESLint + Prettier; Spotless + Checkstyle) and wire format-check as a gate
- [ ] T042 [P] [US5] Add CI workflow stub `.github/workflows/ci.yml` running `make build && make test && make lint` on push/PR (fail-fast)

**Checkpoint**: Quality gates enforceable locally and in CI.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T043 [P] Write `docs/quickstart.md` matching specs/002 quickstart (checkout → running system)
- [ ] T044 [P] Write repo root `README.md`: architecture (cloud LB → Nginx → gateway → services → PostgreSQL), prerequisites, commands
- [ ] T045 Run the quickstart end-to-end and record results (build → up → round trip → failure state → tests/lint → add-a-service)
- [ ] T046 [P] Map every Success Criterion (SC-001..SC-009) to an automated test in `tests/README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (P1)** → no deps
- **Foundational (P2)** → after Setup; BLOCKS all stories
- **US1 (Phase 3)** → after Foundational; the MVP (minimal services + compose bring-up)
- **US2 (Phase 4)** → after US1 (hardens the Dockerfiles/images US1 introduced)
- **US3 (Phase 5)** → after US1 (fleshes out the reference service + gateway route)
- **US4 (Phase 6)** → after US3 (needs the real reference endpoint to display data)
- **US5 (Phase 7)** → after tests exist across stories (formalizes the gate commands)
- **Polish (Phase 8)** → after targeted stories

### Within Each Story

- Tests written and FAILING before implementation (TDD, Principle II)
- Migrations + entity before controller/service; contract before client; services before gateway route

### Parallel Opportunities

- Setup: T002, T003, T004, T005 in parallel
- Foundational: T007–T011 in parallel after T006
- US1: tests T012/T013 in parallel; minimal services T014/T015/T016 and Dockerfiles T017 in parallel (different dirs)
- US3: tests T024–T027 in parallel; T028/T029 in parallel before T030
- US4: tests T033/T034 in parallel; T035/T036 in parallel before T037

---

## Parallel Example: User Story 3

```bash
# Write US3 tests first (must fail):
Task: "Contract test in backend/reference-service/src/test/.../ReferenceContractTest"
Task: "Testcontainers integration test (Flyway + persist/read SampleItem)"
Task: "Unit test for SampleItem validation"
Task: "Migration idempotency test"

# Then build in parallel (different files):
Task: "Flyway V1__create_sample_table.sql"
Task: "SampleItem JPA entity + repository"
```

---

## Implementation Strategy

### MVP First (US1)

1. Setup → 2. Foundational → 3. US1
4. **STOP and VALIDATE**: `make build && make up`, confirm all-healthy + frontend reachable.
5. Demoable: the whole multi-service skeleton boots from two commands.

### Incremental Delivery

1. Setup + Foundational → contract + harness ready
2. US1 → stack boots healthy (MVP)
3. US2 → independently-tagged per-service images + Release Set
4. US3 → real DB-backed reference microservice through the gateway
5. US4 → React UI round trip with error handling
6. US5 → enforced test + lint gates (local + CI)
7. Polish → docs, quickstart run, SC coverage map

### Notes

- [P] = different files, no dependencies
- US1 intentionally builds minimal services so the stack boots early; US3/US4 add real behavior
- Verify each test fails before implementing; commit after each task or logical group (push per project workflow)
