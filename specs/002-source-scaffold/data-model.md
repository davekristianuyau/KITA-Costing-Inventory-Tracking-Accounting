# Phase 1 Structure Model: Application Source Code Scaffolding

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

Because this feature is scaffolding only, its "data model" is the **structure model** — the
directories, placeholders, and scaffolding files created — not runtime/business data. No
`SampleItem` or any persisted entity is created here; the reference microservice's real data
model arrives in a later implementation feature. Only the *location* for it (the Flyway
migration directory) is scaffolded.

## Structural entities

### Repository Skeleton

The complete tree expressing the multi-service architecture. Required top-level members:
`frontend/`, `backend/`, `contracts/`, `docs/`, `docker-compose.yml`, `Makefile`, `README.md`,
`.gitignore`.

### Service Directory

| Field | Value / Rule |
|-------|--------------|
| name | `frontend`, `gateway`, or `reference-service` |
| build_file | present as a skeleton (`package.json` / module `build.gradle.kts`) |
| dockerfile | present as a skeleton |
| placeholder_substructure | source/resources/test dirs preserved via `.gitkeep` |
| application_code | MUST be absent (FR-013) |

### Scaffolding File (inventory the feature must produce)

| File | Purpose | Behavioral? |
|------|---------|-------------|
| `backend/settings.gradle.kts` | declares `:gateway`, `:reference-service` | no |
| `backend/build.gradle.kts` | root toolchain + quality-gate plugins | no |
| `backend/config/checkstyle.xml`, spotless rules | backend quality-gate config | no |
| `backend/*/build.gradle.kts` | per-module build skeleton | no |
| `backend/*/Dockerfile` | image build skeleton | no |
| `frontend/package.json` | intended deps + script targets | no |
| `frontend/tsconfig.json`, `vite.config.ts` | frontend build config | no |
| `frontend/.eslintrc`, `.prettierrc` | frontend quality-gate config | no |
| `frontend/nginx.conf` | serve static + `/api` proxy (skeleton) | no |
| `frontend/Dockerfile` | node build → nginx (skeleton) | no |
| `docker-compose.yml` | wires postgres/gateway/reference-service/frontend | no |
| `Makefile` | `build`/`up`/`test`/`lint` target skeletons | no |
| `contracts/openapi.yaml` | placed source-of-truth contract | no |
| `.gitignore` | build outputs / local artifacts | no |
| `README.md`, `docs/*` | architecture, add-a-service, quickstart | no |

### Placeholder

A `.gitkeep` or short README preserving an otherwise-empty directory and stating its purpose;
required in every empty directory (FR-002, SC-004).

### Migration Location (scaffolded, empty)

`backend/reference-service/src/main/resources/db/migration/` exists with a placeholder. It is
the Flyway location future features use; it contains **no** migration files in this feature.

## Structural invariants

- **No behavior**: no application source, functional tests, or migration files exist (FR-013,
  SC-003).
- **Completeness**: every file in the Scaffolding File inventory is present (SC-002).
- **Empty-dir preservation**: no intended directory is missing due to being empty (FR-002).
- **Architecture fidelity**: the tree matches the resolved architecture so later features fill
  it in without restructuring (FR-014).
- **Template clarity**: `reference-service/` is laid out so its structure is the obvious model
  for adding a new service (SC-006).
