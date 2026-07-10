# Phase 0 Research: Application Source Code Scaffolding

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

Resolves the technical choices implied by the spec and its clarifications. Framework/language
families (React, Spring Boot, microservices, Nginx, OpenAPI, PostgreSQL) are fixed by the
project owner; this document pins the specific tools and patterns.

> **Scope note**: Feature 002 is **scaffolding only** — it creates the folder structure and
> config/skeleton files that reflect these decisions, but implements no application behavior.
> The decisions below define the architecture the skeleton targets and the toolchains its
> config files declare; the actual code, migrations, and tests are built in later features.

## R1. Frontend stack

- **Decision**: React 18 + **TypeScript** + **Vite**; React Router for navigation; state kept
  minimal (React Query optional later). Built to static assets.
- **Rationale**: TypeScript pairs with the generated OpenAPI client for end-to-end type safety
  and supports the lint/quality gates (constitution VII). Vite is the fast, standard React
  build tool. React was chosen (spec 002) to share patterns with a future React Native app.
- **Alternatives considered**: Create-React-App (deprecated/slow) — rejected; Next.js — rejected
  (SSR/framework overhead unnecessary for an authenticated SPA behind a gateway).

## R2. Serving the frontend + edge caching (Nginx)

- **Decision**: Frontend container is multi-stage: Node builds static assets, **Nginx** serves
  them with cache-control + gzip and reverse-proxies `/api/*` to the API gateway. Nginx does
  **not** terminate TLS.
- **Rationale**: Matches the clarified two-layer decision; Nginx is the standard, efficient way
  to serve a React build and is where HTTP caching pays off. TLS/DNS/custom domain are handled
  by feature 001's cloud load balancer.
- **Alternatives considered**: Serving static assets from the gateway — rejected (mixes edge
  caching with backend routing); a CDN — deferred to feature 001/ops.

## R3. Backend build & service layout

- **Decision**: Java 21 + Spring Boot 3.3; **Gradle (Kotlin DSL) multi-module** backend with
  modules `gateway` and `reference-service`; each produces its own image.
- **Rationale**: Multi-module keeps shared build config (Spotless, Checkstyle, dependency
  versions) in one place while each service stays independently buildable/deployable. Gradle
  is fast and common for multi-module JVM builds.
- **Alternatives considered**: Maven multi-module — viable, rejected for slower iteration;
  separate repos per service — rejected (premature for a scaffold; a monorepo is simpler to
  bootstrap and matches feature 001's single-repo pipeline).

## R4. API gateway

- **Decision**: **Spring Cloud Gateway** (reactive) as a dedicated service; routes configured
  statically in `application.yml`, overridable via environment variables; exposes an aggregate
  health view via Actuator.
- **Rationale**: Same Java/Spring toolchain as the services; programmable filters make it the
  natural home for the future auth feature, rate limiting, and circuit breaking. Static routes
  avoid a discovery server (YAGNI) while the service count is small.
- **Alternatives considered**: Netflix Eureka + client-side discovery — rejected (unneeded
  moving part now); Kong/Traefik as API gateway — rejected (adds a non-Java component for
  backend routing when Spring Cloud Gateway suffices).

## R5. Persistence & migrations

- **Decision**: PostgreSQL 16 + Spring Data JPA (Hibernate) in the reference service;
  **Flyway** for versioned SQL migrations (`db/migration/V1__create_sample_table.sql`); writes
  are `@Transactional`.
- **Rationale**: Flyway's plain-SQL, forward-versioned migrations are simple and match the
  constitution's "versioned, repeatable migrations" discipline. PostgreSQL matches feature
  001's managed database. JPA is the standard Spring persistence layer.
- **Alternatives considered**: Liquibase (XML/YAML changelogs) — rejected for heavier authoring
  vs plain SQL; schema auto-generation via `ddl-auto=update` — rejected (not safe/repeatable,
  violates migration discipline).

## R6. API contract (OpenAPI, contract-first)

- **Decision**: A hand-authored **`contracts/openapi.yaml`** is the source of truth. The
  frontend generates a typed client from it (openapi-typescript + openapi-fetch). The backend
  is validated against it by **contract tests** (OpenAPI request/response validation, e.g.,
  atlassian swagger-request-validator) rather than full server-stub generation.
- **Rationale**: Honors the clarified contract-first decision, gives the frontend type safety,
  and lets tests fail if a service drifts from the contract (FR-009/009a) — all without the
  maintenance cost of generated server stubs.
- **Alternatives considered**: Code-first via springdoc (generate spec from annotations) —
  rejected (makes code, not the contract, the source of truth); full server-stub generation
  (openapi-generator) — rejected (heavier, brittle regen for a scaffold).

## R7. Local orchestration

- **Decision**: **Docker Compose** brings up postgres, reference-service, gateway, and the
  Nginx/frontend container. A `Makefile` exposes the documented single commands: `make build`,
  `make up`, `make test`, `make lint`.
- **Rationale**: Compose is the de-facto standard for multi-container local dev and gives the
  "single command to run everything" the spec requires (FR-002/015).
- **Alternatives considered**: Tilt/Skaffold — rejected (Kubernetes-oriented, overkill for a
  scaffold); bare shell scripts — rejected (Compose already models the service graph + networks).

## R8. Testing & quality gates

- **Decision**: Backend — JUnit 5, Spring Boot Test, **Testcontainers** (real PostgreSQL) for
  integration + migration tests, OpenAPI validator for contract tests; **Spotless**
  (google-java-format) + **Checkstyle**. Frontend — **Vitest** + React Testing Library; ESLint
  + Prettier. All wired into `make test` / `make lint`.
- **Rationale**: Testcontainers exercises the real migration + persistence path (SC-009);
  contract tests enforce FR-009a; the format/lint tools satisfy the constitution's automated
  quality gates (VII) and TDD (II).
- **Alternatives considered**: H2 in-memory DB for tests — rejected (dialect drift hides
  PostgreSQL-specific bugs); no contract test — rejected (would let services silently drift).

## R9. Observability & configuration

- **Decision**: **Structured JSON logging** (Logback encoder) per service; **Spring Boot
  Actuator** `/health` (+ `/info` with version) on each service; the gateway aggregates
  downstream health. All configuration (DB URL/credentials, gateway routes, service URLs) is
  read from environment variables (12-factor); local defaults live in Compose.
- **Rationale**: Satisfies observability (constitution V) and externalized config (FR-014); the
  health endpoints are what feature 001's pipeline and the aggregate-health requirement rely on.
- **Alternatives considered**: Plain-text logs — rejected (harder to aggregate in cloud);
  bundling config into images — rejected (breaks environment portability).

## Deferred (not blocking)

- **Authentication/authorization** — separate feature; the gateway is structured to host it.
- **Application runtime performance targets** — set when real domains are implemented.
- **Per-service databases** — a shared per-environment database is assumed (feature 001);
  revisit if a domain needs isolation.
