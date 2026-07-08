# Implementation Plan: Application Source Code Scaffolding

**Branch**: `002-source-scaffold` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-source-scaffold/spec.md`

## Summary

Create the **repository skeleton only** for KITA's multi-service application: the complete
folder structure plus the non-application config/scaffolding files that fix the architecture on
disk — backend Gradle multi-module files, a frontend package/config manifest and tool configs,
per-service Dockerfile skeletons, a `docker-compose.yml`, a `Makefile`, quality-gate configs,
the placed `contracts/openapi.yaml`, `.gitignore`, and documentation. **No application code, no
functional tests, no behavioral migrations** — those land in later per-service features that
fill in this skeleton. The layout reflects the resolved architecture (React+Nginx frontend →
Spring Cloud Gateway → Spring Boot reference microservice → PostgreSQL, OpenAPI contract-first).

## Technical Context

**Language/Version**: Config/scaffolding only. Declares the intended toolchains without using
them: TypeScript/Node 22 + Vite (frontend), Java 21 + Spring Boot 3.3 + Gradle Kotlin DSL
(backend), Nginx, Docker Compose. No source is compiled or executed in this feature.
**Primary Dependencies**: Declared as intent in manifests/build files (React, React Router,
Spring Web/Gateway/Data JPA/Flyway/Actuator, quality-gate plugins) — not exercised.
**Storage**: PostgreSQL is scaffolded as a Compose service placeholder and a Flyway migration
directory placeholder; no schema or migration behavior.
**Testing**: None in this feature — there is no application behavior to test. Quality-gate tool
*configs* are placed as scaffolding; test suites arrive with the code in later features. (See
Constitution Check for why TDD does not apply here.)
**Target Platform**: A version-controlled repository skeleton on any OS; files describe
Linux-container targets to be built later.
**Project Type**: Repository/project scaffolding (structure + config), pre-implementation.
**Performance Goals**: N/A (no runtime).
**Constraints**: No application logic/tests/behavioral migrations (FR-013); empty directories
preserved via placeholders (FR-002); structure must match the resolved architecture so later
features need no restructuring (FR-014); files implying behavior are clearly marked skeletons.
**Scale/Scope**: One frontend, one gateway, one reference-service (template), one contracts
dir, one docs set, plus root orchestration/build files. Bounded and small by design.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified, scope-corrected spec + this plan precede any file creation | PASS |
| II. Test-Driven Development | TDD governs application/bugfix code; this feature writes **no application code**, so there is nothing to test. Test harness + suites are scaffolded/authored in the later implementation features that add behavior. | PASS (N/A by scope — recorded, not skipped) |
| III. Security & Data Integrity First | No code/secrets created; `.gitignore` excludes local secrets/artifacts; migration/secret conventions are documented for later features | PASS |
| IV. Environment Isolation | Compose/config scaffolding keeps configuration externalized by design; no environments provisioned | PASS |
| V. Observability & Debuggability | Logging/health conventions documented in scaffolding; implemented with the services later | PASS |
| VI. Simplicity & YAGNI | Structure limited to one reference service + shared dirs; no speculative modules | PASS with the multi-service justification below |
| VII. Automated Quality Gates | Quality-gate tool *configs* + Makefile/CI skeletons are placed so gates are ready when code arrives | PASS |

Initial Constitution Check: **PASS**. Post-Design Check: **PASS** — a pure-scaffolding feature
introduces no behavioral surface; the one standing complexity is the multi-service structure.

> **Note on TDD:** Principle II is NON-NEGOTIABLE for code. Because this feature deliberately
> contains no application logic (user-confirmed scaffolding-only scope), there is no behavior to
> cover. The first implementation feature that adds behavior MUST follow TDD, using the
> quality-gate/test-tool configs scaffolded here.

## Project Structure

### Documentation (this feature)

```text
specs/002-source-scaffold/
├── plan.md              # This file
├── spec.md              # Scaffolding-only spec (with Clarifications)
├── research.md          # Phase 0 — architecture decisions the layout reflects
├── data-model.md        # Phase 1 — the STRUCTURE model (no runtime data)
├── quickstart.md        # Phase 1 — how to verify the scaffold
├── contracts/           # Phase 1 — placed contract + convention docs
│   ├── openapi.yaml            # Source-of-truth API contract (placed; implemented later)
│   ├── service-template.md     # Add-a-service pattern (documentation)
│   └── container-contract.md   # Per-service image contract (documentation)
└── checklists/
    └── requirements.md
```

### Source Code (repository root) — the skeleton this feature creates

```text
frontend/                          # React + TS + Vite (config/manifests only), Nginx edge
├── src/                           # placeholder (.gitkeep); components added later
├── tests/                         # placeholder; suites added later
├── package.json                   # intended deps + script targets (no components)
├── tsconfig.json / vite.config.ts # config skeletons
├── .eslintrc / .prettierrc        # quality-gate configs
├── nginx.conf                     # skeleton (serve static + /api proxy)
└── Dockerfile                     # skeleton (node build → nginx)

backend/
├── settings.gradle.kts            # declares :gateway, :reference-service
├── build.gradle.kts               # root toolchain + Spotless/Checkstyle (no app code)
├── config/                        # checkstyle.xml, spotless rules (quality-gate configs)
├── gateway/
│   ├── src/main/java/             # placeholder (.gitkeep)
│   ├── src/main/resources/        # placeholder application.yml skeleton
│   ├── build.gradle.kts           # module build skeleton
│   └── Dockerfile                 # skeleton
└── reference-service/             # THE TEMPLATE for future services
    ├── src/main/java/             # placeholder (.gitkeep)
    ├── src/main/resources/
    │   └── db/migration/          # placeholder (.gitkeep) — Flyway location, no migrations
    ├── src/test/java/             # placeholder
    ├── build.gradle.kts           # module build skeleton
    └── Dockerfile                 # skeleton

contracts/
└── openapi.yaml                   # placed source-of-truth contract

docs/
├── architecture.md                # layout + architecture overview
├── add-a-service.md               # the template pattern
└── quickstart.md                  # how the pieces fit (fleshed out as code lands)

docker-compose.yml                 # intended services wiring (scaffold)
Makefile                           # build/up/test/lint target skeletons
README.md                          # architecture + layout
.gitignore                         # build outputs / local artifacts
```

**Structure Decision**: Establish the full multi-service tree with placeholders and the
config/scaffolding files listed above; no application code. This satisfies the scaffolding-only
scope (FR-013), preserves empty dirs (FR-002), and fixes the architecture (FR-014) so later
features implement in place without restructuring.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Multi-service skeleton (frontend + Nginx + gateway + reference-service) rather than a single folder | The approved architecture is microservices (spec 001/002); the skeleton must express that shape so future features slot in without restructuring | A single-app skeleton is simpler but would misrepresent the agreed architecture and force a disruptive reorganization once real services are added. Complexity is bounded: one reference service as the template, everything else a placeholder. |
