# Feature Specification: Application Source Code Scaffolding

**Feature Branch**: `002-source-scaffold`
**Created**: 2026-07-08
**Status**: Implemented (scaffold delivered)
**Input**: User description: "scaffolding the source code, this will be in JS, react, vue if possible, and java springboot and microservices, frontend and backend runs in 1 container" + "this should be scaffolding only, no code involved yet — folder structure and config skeletons only"
**Resolved decisions**: Frontend = React (to share patterns with a planned React Native
Android app); Backend = true microservices (multiple containers); Gateway = Nginx edge +
Spring Cloud Gateway; Contract = OpenAPI (REST/JSON); Persistence target = PostgreSQL + Flyway.
**Scope note**: This feature delivers the **repository skeleton only** — the complete folder
structure and the non-application config/scaffolding files that define the architecture. It
writes NO application logic, NO functional tests, and NO behavioral database migrations. Those
are implemented in later, per-service features that fill in this skeleton.

## Overview

This feature establishes the on-disk skeleton for KITA's multi-service application so that its
architecture is visible and every future feature has an obvious place to add code. It creates
the directory layout and the config/scaffolding files (build files, container/orchestration
files, quality-gate configs, the placed OpenAPI contract, and documentation) for a React
frontend served by Nginx, a Spring Cloud Gateway, and Java/Spring Boot microservices (starting
with a single reference-service directory used as the template). It deliberately contains no
application code — running services, persistence behavior, UI logic, and tests arrive in
subsequent features.

## Clarifications

### Session 2026-07-08

- Q: How many services does the scaffold stand up initially? → A: Gateway + frontend +
  one generic reference microservice only; real domains (costing/inventory/accounting)
  are added later via the documented "add a service" pattern.
- Q: Does the reference service include real database persistence? → A: Persistence target is
  PostgreSQL + Flyway; the migration directory and conventions are scaffolded now, but the
  actual migration/behavior is implemented in a later feature (this feature is scaffolding only).
- Q: How is the gateway implemented? → A: Two layers — Nginx at the edge (serves the built
  React static assets, HTTP caching/compression, reverse-proxies `/api/*`) and a dedicated
  Spring Cloud Gateway service for backend API routing. TLS/DNS/custom domain are handled by
  feature 001's cloud load balancer. Scaffolded here as directories + skeleton config only.
- Q: What form does the frontend↔backend interface contract take? → A: REST/JSON with an
  OpenAPI specification as the source of truth; the contract file is placed in this feature,
  its endpoints implemented later.
- Q: For "scaffolding only", where is the line on "no code"? → A: Folder structure PLUS
  non-application config/scaffolding files (build files, Dockerfiles, docker-compose, Makefile,
  quality-gate configs, README/docs, .gitignore/.gitkeep, placed OpenAPI contract). No
  application logic, no functional tests, no behavioral migrations.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navigable repository structure (Priority: P1)

A developer clones the repository and can immediately see, from the folder structure alone,
where every part of the multi-service application belongs: the React frontend, the Nginx edge,
the Spring Cloud Gateway, the reference microservice (the template for future services), the
shared API contract, and documentation.

**Why this priority**: The primary value of this feature is a clear, agreed skeleton that
prevents an ad-hoc layout later. Everything else (build files, docs) hangs off this structure.

**Independent Test**: On a clean checkout, inspect the tree; confirm every planned component
has a dedicated directory with a placeholder, and no directory's purpose is ambiguous.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** a developer lists the repository, **Then** there are
   dedicated directories for `frontend/`, `backend/gateway/`, `backend/reference-service/`,
   `contracts/`, and `docs/`, each with a placeholder or README explaining its purpose.
2. **Given** the structure, **When** a developer looks for where a new microservice would go,
   **Then** the `backend/reference-service/` layout makes the pattern obvious.
3. **Given** the structure, **When** it is inspected, **Then** it contains no application
   source code, functional tests, or behavioral migrations (scaffolding only).

---

### User Story 2 - Build, container, and orchestration scaffolding files (Priority: P1)

The skeleton includes the non-application files that define how the system will be built and
run: backend Gradle multi-module files, a frontend package/config manifest, per-service
Dockerfile skeletons, a docker-compose file wiring the intended services, a Makefile with the
documented command targets, and a `.gitignore` — all as scaffolding, with no application code.

**Why this priority**: These files declare the build/run architecture and the conventions
future features must follow; placing them now fixes the shape without implementing behavior.

**Independent Test**: Inspect the config/scaffolding files; confirm they reference the correct
directories/services and document the intended build/run commands, while containing no
application logic.

**Acceptance Scenarios**:

1. **Given** the skeleton, **When** a developer opens the backend build files, **Then**
   `settings.gradle.kts` declares the `gateway` and `reference-service` modules and the root
   build declares the intended toolchain and quality-gate plugins (no application classes).
2. **Given** the skeleton, **When** a developer opens `docker-compose.yml`, **Then** it defines
   the intended services (postgres, gateway, reference-service, frontend) and their wiring as
   scaffolding, referencing each service's Dockerfile.
3. **Given** the skeleton, **When** a developer opens the `Makefile`, **Then** it documents the
   `build`, `up`, `test`, and `lint` command targets (as skeletons/placeholders).

---

### User Story 3 - Contract and convention scaffolding (Priority: P2)

The shared OpenAPI contract file is placed, quality-gate tool configurations (lint/format) are
present, and the reference-service directory is laid out as the copyable template — so the
project's conventions are established before any code is written.

**Why this priority**: Establishing the contract location and conventions up front keeps future
implementation consistent, but it depends on the base structure (US1) existing first.

**Independent Test**: Confirm `contracts/openapi.yaml` exists as the placed contract, lint/format
config files are present for frontend and backend, and the reference-service directory contains
the placeholder sub-structure (source, resources, migration dir) future code will fill.

**Acceptance Scenarios**:

1. **Given** the skeleton, **When** a developer opens `contracts/openapi.yaml`, **Then** the
   API contract file is present as the source of truth to be implemented later.
2. **Given** the skeleton, **When** a developer inspects quality-gate configs, **Then**
   frontend (ESLint/Prettier) and backend (Spotless/Checkstyle) configuration files are present.
3. **Given** the skeleton, **When** a developer inspects `backend/reference-service/`, **Then**
   it contains the placeholder sub-structure (e.g., `src/main/...`, `src/main/resources/db/migration/`)
   with placeholders, no behavioral content.

---

### User Story 4 - Documentation of structure and conventions (Priority: P2)

The skeleton includes documentation that explains the layout and the conventions future work
follows: a root README with an architecture overview, an "add a service" guide, and a quickstart
that describes how the pieces fit (to be fleshed out as implementation lands).

**Why this priority**: Documentation makes the skeleton self-explanatory for a solo developer
resuming later or onboarding help; it depends on the structure and files it describes.

**Independent Test**: Read the README and docs; confirm they accurately describe the created
structure, the intended architecture, and how to add a new service.

**Acceptance Scenarios**:

1. **Given** the skeleton, **When** a developer reads the root `README.md`, **Then** it
   describes the architecture (cloud LB → Nginx → gateway → services → PostgreSQL) and the
   repository layout.
2. **Given** the skeleton, **When** a developer reads `docs/add-a-service.md`, **Then** it
   explains how a future microservice is added following the reference-service template.

---

### Edge Cases

- What if a directory would otherwise be empty (git does not track empty folders)? Each such
  directory must contain a placeholder (`.gitkeep` or a short README) so the structure is
  preserved in version control.
- What if a config/scaffolding file implies behavior that does not yet exist (e.g., a Makefile
  `test` target)? It must be a documented placeholder/skeleton, clearly not a working
  implementation, so no one mistakes the scaffold for a running system.
- How is it kept honest that this feature contains no application code? A structure review must
  be able to confirm the absence of application source, functional tests, and behavioral
  migrations.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST contain a dedicated directory for each planned component —
  `frontend/`, `backend/gateway/`, `backend/reference-service/`, `contracts/`, `docs/` — each
  with a placeholder or README describing its purpose.
- **FR-002**: Every directory that would otherwise be empty MUST contain a placeholder
  (`.gitkeep` or README) so the structure is preserved under version control.
- **FR-003**: The backend MUST include Gradle multi-module scaffolding: `settings.gradle.kts`
  declaring the `gateway` and `reference-service` modules and a root build file declaring the
  intended toolchain and quality-gate plugins — with no application classes.
- **FR-004**: The frontend MUST include a package/config manifest and configuration files
  (e.g., TypeScript, bundler, lint/format) declaring the intended toolchain — with no
  application components or logic.
- **FR-005**: Each service directory (frontend, gateway, reference-service) MUST include a
  Dockerfile skeleton describing how its image will be built — without building real
  application code.
- **FR-006**: A `docker-compose.yml` MUST define the intended services (postgres, gateway,
  reference-service, frontend) and their wiring as scaffolding, referencing each Dockerfile.
- **FR-007**: A `Makefile` MUST document the intended command targets (`build`, `up`, `test`,
  `lint`) as skeletons/placeholders.
- **FR-008**: The shared OpenAPI contract file MUST be placed at `contracts/openapi.yaml` as the
  source of truth to be implemented later.
- **FR-009**: Quality-gate configuration files MUST be present for the frontend (lint/format)
  and backend (format/style) as scaffolding.
- **FR-010**: The `backend/reference-service/` directory MUST be laid out as the copyable
  template for future services, including placeholder sub-structure for source, resources, and
  a database-migration directory — with no behavioral content.
- **FR-011**: A root `README.md` MUST describe the architecture and repository layout, and
  `docs/` MUST include an "add a service" guide and a quickstart describing the structure.
- **FR-012**: A `.gitignore` MUST be present covering build outputs and local artifacts for the
  chosen toolchains.
- **FR-013**: This feature MUST NOT contain application logic, functional tests, or behavioral
  database migrations; scaffolding files that imply behavior MUST be clearly marked as
  placeholders.
- **FR-014**: The structure MUST reflect the resolved architecture (React+Nginx frontend;
  Spring Cloud Gateway; Spring Boot reference microservice; OpenAPI contract; PostgreSQL+Flyway
  migration location) so later features fill it in without restructuring.

### Key Entities *(include if feature involves data)*

- **Repository Skeleton**: The complete directory tree plus placeholders that expresses the
  multi-service architecture on disk.
- **Service Directory**: A dedicated folder for a service (frontend, gateway, reference-service)
  containing its scaffolding files (build/config, Dockerfile) and placeholder sub-structure.
- **Scaffolding File**: A non-application config/support file that defines build, run, quality,
  or documentation structure (Gradle files, package/config manifests, Dockerfiles, compose,
  Makefile, lint/format configs, `.gitignore`).
- **Interface Contract (placed)**: `contracts/openapi.yaml`, present as the source of truth to be
  implemented in a later feature.
- **Documentation Set**: README + `docs/` describing the architecture, layout, add-a-service
  pattern, and quickstart.
- **Placeholder**: A `.gitkeep` or short README that preserves an otherwise-empty directory and
  states its purpose.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From the structure alone, a developer can correctly identify where every planned
  component (frontend, edge, gateway, reference service, contract, docs) belongs, with no
  ambiguous or missing directories.
- **SC-002**: 100% of the required scaffolding files (per Functional Requirements) are present
  in the checkout, verified against a checklist.
- **SC-003**: The repository contains zero application source files, functional tests, or
  behavioral migrations, verified by inspection (scope guard).
- **SC-004**: Every otherwise-empty directory is preserved in version control via a placeholder.
- **SC-005**: The documentation accurately describes the created structure and the intended
  architecture, verified by cross-checking the docs against the tree.
- **SC-006**: A developer can locate the template for adding a new service and the place for its
  future migrations without guidance beyond the docs.

## Assumptions

- **Scaffolding only**: No application code, functional tests, or behavioral migrations in this
  feature; skeleton/config files may reference future behavior but do not implement it.
- **Architecture is fixed by prior clarifications**: React (Vite/TypeScript) + Nginx frontend;
  Spring Cloud Gateway; Java/Spring Boot reference microservice; OpenAPI contract; PostgreSQL +
  Flyway as the persistence target. These shape the folder layout and which files are stubbed.
- **Single repository, Gradle multi-module backend, Docker Compose** for the eventual local run
  (as decided in planning); expressed here as scaffolding files only.
- **One reference service** is scaffolded as the template; real domains (costing/inventory/
  accounting) are added later via the add-a-service pattern.
- **Feature 001 contract**: The eventual images conform to feature 001's per-service container
  contract; this feature only lays out where those services live.

## Dependencies

- The architectural decisions recorded in this spec's Clarifications (and feature 001's
  multi-service deployment) define the structure to be scaffolded.
- A version-control system that requires placeholders to preserve empty directories.

## Out of Scope

- All application logic (frontend components/behavior, gateway routing behavior, microservice
  business logic and endpoints).
- Functional/automated tests and their execution.
- Behavioral database migrations (only the migration directory/convention is scaffolded).
- A building or running system — `make`/compose/Docker files are skeletons, not guaranteed to
  build or boot real applications in this feature.
- Business logic for costing, inventory, or accounting; the React Native Android app;
  authentication (all future features).
- CI/CD pipeline definition (owned by feature 001).
