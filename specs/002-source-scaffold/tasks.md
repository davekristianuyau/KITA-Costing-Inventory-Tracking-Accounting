---

description: "Task list for Application Source Code Scaffolding (structure + config only)"
---

# Tasks: Application Source Code Scaffolding

**Input**: Design documents from `/specs/002-source-scaffold/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: NONE. This feature is scaffolding only (structure + config/skeleton files); it
contains no application logic, so there is nothing to test. Test suites arrive with the code in
later per-service features (which MUST follow TDD, using the quality-gate configs placed here).

**Organization**: Grouped by user story. US1 = folder structure; US2 = build/container/
orchestration scaffolding files; US3 = contract + quality-gate + template layout; US4 = docs.
Every "create a file" task produces a **skeleton/config only** — no application code, no
functional tests, no behavioral migrations (FR-013).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files/dirs, no dependencies)
- Paths are repository-relative per plan.md.

---

## Phase 1: Setup (Repository root)

- [ ] T001 Create the root `.gitignore` covering Node, Java/Gradle, Docker, and local artifacts
- [ ] T002 [P] Create root `README.md` placeholder (title + one-line project description; filled in US4)
- [ ] T003 [P] Create top-level directories `frontend/`, `backend/`, `contracts/`, `docs/` with `.gitkeep` placeholders

---

## Phase 2: Foundational (Layout conventions)

**⚠️ CRITICAL**: Establishes the tree all stories populate.

- [ ] T004 Define and document the placeholder convention (`.gitkeep` + short `README` per empty dir) in `docs/architecture.md` (stub) so every subsequent directory follows it

**Checkpoint**: Root exists; placeholder convention set.

---

## Phase 3: User Story 1 - Navigable repository structure (Priority: P1) 🎯 MVP

**Goal**: The full multi-service directory tree exists with placeholders, so every component's
location is unambiguous.

**Independent Test**: List the tree; confirm dedicated dirs for frontend, gateway,
reference-service, contracts, docs — each with a placeholder — and no application code.

- [ ] T005 [P] [US1] Create `frontend/` subtree with placeholders: `src/`, `tests/` (`.gitkeep` in each)
- [ ] T006 [P] [US1] Create `backend/gateway/` subtree with placeholders: `src/main/java/`, `src/main/resources/`, `src/test/java/` (`.gitkeep` in each)
- [ ] T007 [P] [US1] Create `backend/reference-service/` subtree with placeholders: `src/main/java/`, `src/main/resources/db/migration/`, `src/test/java/` (`.gitkeep` in each) — the template layout
- [ ] T008 [P] [US1] Add a short `README.md` in each service directory (`frontend/`, `backend/gateway/`, `backend/reference-service/`) stating its purpose
- [ ] T009 [US1] Add `docs/architecture.md` section listing the tree and each directory's purpose (structure map)

**Checkpoint**: The skeleton tree is complete and self-describing. **MVP reached.**

---

## Phase 4: User Story 2 - Build, container, and orchestration scaffolding (Priority: P1)

**Goal**: The non-application files that define how the system is built/run are present as
skeletons referencing the correct directories/services.

**Independent Test**: Open the build/compose/Makefile files; confirm they reference the right
modules/services and document intended commands, with no application code.

- [ ] T010 [P] [US2] Create backend `backend/settings.gradle.kts` declaring modules `:gateway` and `:reference-service`
- [ ] T011 [P] [US2] Create backend root `backend/build.gradle.kts` skeleton: Java 21 toolchain, Spring Boot/Spring Cloud BOM references, Spotless + Checkstyle plugins (no source)
- [ ] T012 [P] [US2] Create per-module build skeletons `backend/gateway/build.gradle.kts` and `backend/reference-service/build.gradle.kts` (declare intended deps; no code)
- [ ] T013 [P] [US2] Create `frontend/package.json` (intended deps + `build`/`test`/`lint` script placeholders), `tsconfig.json`, and `vite.config.ts`
- [ ] T014 [P] [US2] Create Dockerfile skeletons: `frontend/Dockerfile` (node build → nginx), `backend/gateway/Dockerfile`, `backend/reference-service/Dockerfile`
- [ ] T015 [P] [US2] Create `frontend/nginx.conf` skeleton (serve static assets + `/api` proxy, commented as placeholder)
- [ ] T016 [US2] Create `docker-compose.yml` wiring postgres + gateway + reference-service + frontend (build contexts → each Dockerfile, env placeholders, network) as scaffolding
- [ ] T017 [US2] Create `Makefile` with `build`/`up`/`test`/`lint` target skeletons (documented placeholders, clearly not yet functional)
- [ ] T018 [P] [US2] Create `.env.example` documenting intended env vars (DB creds, `GATEWAY_URL`, `REFERENCE_SERVICE_URL`)

**Checkpoint**: The build/run architecture is expressed in scaffolding files.

---

## Phase 5: User Story 3 - Contract and convention scaffolding (Priority: P2)

**Goal**: The API contract is placed and quality-gate configs + the service template layout are
established.

**Independent Test**: Confirm `contracts/openapi.yaml` exists, lint/format configs are present
for both sides, and `reference-service/` is a clear copyable template.

- [ ] T019 [P] [US3] Place `contracts/openapi.yaml` (source-of-truth API contract, to be implemented later)
- [ ] T020 [P] [US3] Add frontend quality-gate configs `frontend/.eslintrc` and `frontend/.prettierrc`
- [ ] T021 [P] [US3] Add backend quality-gate configs `backend/config/checkstyle.xml` and Spotless config in root build
- [ ] T022 [US3] Confirm/annotate `backend/reference-service/` as the copyable template (a `TEMPLATE.md` noting what to copy and where migrations/resources go)

**Checkpoint**: Contract location + conventions + template established.

---

## Phase 6: User Story 4 - Documentation of structure and conventions (Priority: P2)

**Goal**: Documentation explains the layout, architecture, and how future work fills the skeleton.

**Independent Test**: Read README + docs; confirm they match the created tree and explain
add-a-service.

- [ ] T023 [US4] Flesh out root `README.md`: architecture overview (cloud LB → Nginx → gateway → services → PostgreSQL), repository layout, and scaffolding-only status
- [ ] T024 [P] [US4] Write `docs/add-a-service.md` from contracts/service-template.md (how to add a future microservice using the reference-service template)
- [ ] T025 [P] [US4] Write `docs/quickstart.md` from specs/002 quickstart (how to verify the scaffold; note nothing runs yet)

**Checkpoint**: The skeleton is self-explanatory.

---

## Phase 7: Polish & Verification

- [ ] T026 Verify the scope guard: repository contains no application source, functional tests, or migration files (FR-013, SC-003)
- [ ] T027 [P] Verify every required scaffolding file from data-model.md is present (SC-002) and every empty directory has a placeholder (SC-004)
- [ ] T028 [P] Cross-check `docs/architecture.md` against the actual tree for accuracy (SC-005)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → no deps
- **Foundational (Phase 2)** → after Setup; sets the placeholder convention
- **US1 (Phase 3)** → after Foundational; the tree (MVP)
- **US2 (Phase 4)** → after US1 (files live inside the created dirs)
- **US3 (Phase 5)** → after US1; independent of US2 (different files)
- **US4 (Phase 6)** → after US1–US3 (documents what was created)
- **Polish (Phase 7)** → last (verifies completeness + scope guard)

### Parallel Opportunities

- Setup: T002, T003 in parallel
- US1: T005–T008 in parallel (different dirs)
- US2: T010–T015 and T018 in parallel (different files); T016/T017 after Dockerfiles exist
- US3: T019–T021 in parallel
- US4: T024, T025 in parallel

---

## Parallel Example: User Story 2

```bash
# Create scaffolding files in parallel (different files):
Task: "backend/settings.gradle.kts (modules)"
Task: "backend/build.gradle.kts (root toolchain + quality plugins)"
Task: "frontend/package.json + tsconfig + vite.config.ts"
Task: "Dockerfile skeletons for the three services"
Task: "frontend/nginx.conf skeleton"
```

---

## Implementation Strategy

### MVP First (US1)

1. Setup → 2. Foundational → 3. US1 (the tree)
4. **STOP and VALIDATE**: the full structure exists with placeholders and is self-describing.

### Incremental Delivery

1. Setup + Foundational → root + conventions
2. US1 → complete navigable tree (MVP)
3. US2 → build/container/orchestration scaffolding files
4. US3 → contract + quality-gate configs + template
5. US4 → documentation
6. Polish → scope-guard + completeness verification

### Notes

- No test tasks: nothing executes in this feature (scaffolding only).
- Every file task is a skeleton/config; mark behavior-implying files as placeholders (FR-013).
- Commit after each phase and push per project workflow.
