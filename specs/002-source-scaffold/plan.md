# Implementation Plan: Application Source Code Scaffolding

**Branch**: `002-source-scaffold` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-source-scaffold/spec.md`

## Summary

Scaffold a runnable, testable multi-service skeleton for KITA: a **React (TypeScript, Vite)**
frontend served by **Nginx** (static + caching + `/api` proxy), a **Spring Cloud Gateway** API
gateway, and one generic **reference microservice** (Spring Boot + Spring Data JPA + Flyway)
with real **PostgreSQL** persistence and a sample table. The frontend↔backend contract is an
**OpenAPI** spec (contract-first) that drives a generated TypeScript client and backend
contract tests. Everything builds into **one container image per service** and runs together
locally via **Docker Compose** with a single command. These images are the artifacts the
multi-cloud pipeline (feature 001) deploys. This feature delivers structure, tooling, and a
thin persistence-backed vertical slice — no costing/inventory/accounting business logic.

## Technical Context

**Language/Version**: Frontend — TypeScript 5.x on Node 22 LTS (React 18, Vite 5). Backend —
Java 21 LTS (Spring Boot 3.3.x, Spring Cloud Gateway 2023.x). Orchestration — Docker Compose;
Nginx 1.27 (stable).
**Primary Dependencies**: React Router, generated OpenAPI TS client (openapi-typescript +
openapi-fetch); Spring Web, Spring Data JPA (Hibernate), Flyway, Spring Boot Actuator, Bean
Validation; Gradle (Kotlin DSL) multi-module backend build.
**Storage**: PostgreSQL 16 (local via Compose; managed PostgreSQL from feature 001 in cloud).
Schema owned by the reference service and evolved through Flyway migrations.
**Testing**: Frontend — Vitest + React Testing Library; ESLint + Prettier. Backend — JUnit 5,
Spring Boot Test, Testcontainers (PostgreSQL) for integration + migration tests, OpenAPI
request/response validation for contract tests; Spotless (google-java-format) + Checkstyle.
**Target Platform**: Linux containers (one image per service), runnable locally on
macOS/Windows/Linux via Docker Compose; images consumable by feature 001's pipeline.
**Project Type**: Multi-service web application (frontend + gateway + backend microservice).
**Performance Goals**: Not an app-runtime concern in this feature. Scaffold targets: cold
`docker compose up` to all-healthy in < 3 minutes; full test+lint suite in < 5 minutes locally.
**Constraints**: One container image per service; single-command build and single-command run;
config externalized (12-factor) so images run unchanged across environments; secrets never
hard-coded; OpenAPI is the single source of truth; only Nginx/frontend + gateway are entry
points (backend services private on the Compose network).
**Scale/Scope**: Skeleton — 4 runtime containers (Nginx/frontend, gateway, reference-service,
postgres). One reference domain, one sample table, one end-to-end round trip. Designed so new
services are added by copying the reference-service module pattern.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Specification-Driven Development | Clarified spec + this plan verify compliance before build | PASS — spec.md complete, 4 clarifications resolved |
| II. Test-Driven Development | Contract tests (OpenAPI), migration/integration tests (Testcontainers), frontend component tests — written before implementation | PASS — TDD ordering enforced in tasks |
| III. Security & Data Integrity First | DB credentials via env/Compose secrets (never in code); Bean Validation at API boundaries; `@Transactional` writes; TLS handled at cloud edge (001); money uses BigDecimal when domains arrive | PASS — patterns established by the reference slice |
| IV. Environment Isolation | All config externalized; local Compose is a dev environment distinct from STG/PROD (feature 001) | PASS |
| V. Observability & Debuggability | Structured (JSON) logging via Logback; Actuator `/health` per service; gateway aggregate health | PASS — FR-003/018 (spec 001) alignment |
| VI. Simplicity & YAGNI | One reference service; static gateway routing (no discovery server); Flyway SQL (no heavier tooling); managed frameworks over custom | PASS with justification — see Complexity Tracking |
| VII. Automated Quality Gates | One documented command runs build/test/lint across the repo; fails on lint/test failure; CI-ready | PASS — FR-010/011 |

Initial Constitution Check: **PASS** (one justified complexity — the multi-service topology).
Post-Design Constitution Check (after Phase 1): **PASS** — the OpenAPI contract and reference
module keep the design cohesive; no new principle tension introduced by the data model or
contracts.

## Project Structure

### Documentation (this feature)

```text
specs/002-source-scaffold/
├── plan.md              # This file
├── spec.md              # Feature spec (with Clarifications)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── openapi.yaml            # Source-of-truth API contract (reference endpoint)
│   ├── service-template.md     # The "add a new microservice" pattern (FR-008)
│   └── container-contract.md   # Per-service image contract consumed by feature 001
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
frontend/                          # React + TypeScript + Vite, served by Nginx
├── src/
│   ├── app/                        # shell, routing, layout
│   ├── features/reference/         # view that consumes the reference service
│   ├── api/                        # generated OpenAPI TS client (from contracts/openapi.yaml)
│   └── main.tsx
├── tests/                          # Vitest + React Testing Library
├── Dockerfile                      # multi-stage: node build → nginx serve
├── nginx.conf                      # static serving, caching, gzip, /api proxy → gateway
├── package.json
└── .eslintrc / .prettierrc

backend/
├── settings.gradle.kts             # multi-module: gateway, reference-service
├── build.gradle.kts                # shared config (Spotless, Checkstyle, versions)
├── gateway/                        # Spring Cloud Gateway
│   ├── src/main/java/... 
│   ├── src/main/resources/application.yml   # static routes (env-overridable)
│   ├── src/test/java/...
│   └── Dockerfile
└── reference-service/              # reference microservice
    ├── src/main/java/...           # controller, service (@Transactional), JPA entity, repo
    ├── src/main/resources/
    │   ├── application.yml
    │   └── db/migration/           # Flyway V1__create_sample_table.sql
    ├── src/test/java/...           # unit, Testcontainers integration, OpenAPI contract tests
    └── Dockerfile

contracts/
└── openapi.yaml                    # single source of truth (copy/symlink of specs contract)

docker-compose.yml                  # postgres + gateway + reference-service + frontend(nginx)
Makefile                            # documented single commands: build / up / test / lint
docs/
├── quickstart.md                   # checkout → running system
└── add-a-service.md                # the FR-008 pattern
```

**Structure Decision**: Multi-service layout. `frontend/` (React+Nginx) and `backend/`
(Gradle multi-module: `gateway`, `reference-service`) each build one image per service;
`contracts/openapi.yaml` is the shared source of truth; `docker-compose.yml` + `Makefile`
provide the single-command build/run/test/lint. This satisfies the per-service-image (FR-005),
single-command (FR-001/002/015), and add-a-service (FR-008) requirements while keeping the
scaffold minimal.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Multi-service topology (frontend + Nginx + gateway + reference service) instead of a single app | The project owner explicitly chose true microservices (spec 002 clarification); the scaffold must establish that shape so future domains are independently deployable | A single Spring Boot + embedded UI monolith is simpler but contradicts the approved microservices direction and would misrepresent how every future service is built/deployed. Complexity is bounded by scaffolding only ONE reference service and using static routing + managed frameworks. |
| Two front-tier layers (Nginx edge + Spring Cloud Gateway) | Nginx gives HTTP caching + static React serving; the gateway gives programmable backend routing and a home for future auth — distinct concerns | Collapsing to one layer either loses caching/static optimization (gateway-only) or pushes backend routing/auth into Nginx config (Nginx-only), which is harder to evolve. User approved the two-layer split. |
