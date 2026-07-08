# Quickstart: Application Source Code Scaffolding

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

Go from a clean checkout to the running multi-service system. This is the developer happy path
and doubles as the manual acceptance walkthrough. Target: under 45 minutes (SC-001).

## Prerequisites

- Docker + Docker Compose
- Node 22 LTS (frontend dev/build) and JDK 21 (backend dev) — only needed for local dev outside
  containers; the documented build runs inside Docker.
- Make (to run the documented single commands)

## 1. Build everything

```bash
make build      # builds frontend (Vite → Nginx image), gateway, reference-service images
```

Expected: all service images build from a clean checkout with no manual edits (FR-001, SC-002).

## 2. Run the whole system

```bash
make up         # docker compose up: postgres + reference-service + gateway + frontend
```

Expected: Flyway applies `V1__create_sample_table.sql`; every service reports `/health` = UP;
the gateway aggregate health is UP; the frontend is served at http://localhost:8080.

## 3. Verify the end-to-end round trip

- Open http://localhost:8080 → the app shell + landing view renders.
- Navigate to the reference view → it lists sample items fetched via `/api/reference/items`
  (Nginx → gateway → reference-service → PostgreSQL) (SC-004).
- Create an item in the UI (or `POST /api/reference/items`) → it persists and appears on reload
  (proves the DB read/write slice, FR-019, SC-009).

## 4. Verify failure handling

- `docker compose stop reference-service` → the frontend reference view shows a clear error
  state, not a blank/crashed page (SC-007); gateway aggregate health flips to DOWN (FR-003).

## 5. Run tests and lint

```bash
make test       # frontend Vitest + backend JUnit/Testcontainers + OpenAPI contract tests
make lint       # ESLint/Prettier + Spotless/Checkstyle
```

Expected: at least one passing test per service and the frontend; contract tests confirm the
reference service conforms to `contracts/openapi.yaml`; lint reports compliance and fails on a
deliberately malformed sample (SC-006).

## 6. Add a new service (pattern check)

Follow [contracts/service-template.md](./contracts/service-template.md) to add a throwaway
`ping-service`; confirm it routes at `/api/ping/**` and that no existing service's source
changed (FR-008, SC-005).

## Acceptance mapping

| Step | Validates |
|------|-----------|
| 1 | US1/US2, FR-001/005, SC-002 |
| 2 | US1, FR-002/018, SC-003 |
| 3 | US4, FR-004/019, SC-004/009 |
| 4 | Edge cases, FR-003/013, SC-007 |
| 5 | US5, FR-009a/010/011, SC-006 |
| 6 | US3, FR-008, SC-005 |
