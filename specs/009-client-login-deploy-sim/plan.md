# Implementation Plan: Client Login & Per-Client Deployment Simulation

**Branch**: `009-client-login-deploy-sim` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-client-login-deploy-sim/spec.md`

## Summary

Deliver the frontend **login page** (first real frontend), a new **central identity service** that
authenticates users and maps each to their client + backend, and a **tenant-aware edge gateway** that routes
authenticated `/api` traffic to that user's isolated client backend. Prove the whole model with a **local
Docker simulation**: two isolated client stacks (each the feature-008 backend, run as separate Compose
projects), the identity service, the edge, and the frontend — plus a **LocalStack** stand-in that imitates one
client's AWS production deployment. Auth is an **asymmetrically-signed JWT** carrying a `client` claim; the edge
validates it, routes by claim, and forwards the trusted `X-Kita-*` headers the backend services already consume
— so tenant isolation is enforced at the edge *and* at each client backend.

*(Clarified 2026-07-18 — refines the provisional choices: JWT is **asymmetrically signed** (only identity can
mint), carried to the browser in an **httpOnly, encrypted (JWE) cookie** with a **90-minute** lifetime; the
login form collects **company + username + password**, usernames unique per client. Technical Context /
research.md updated accordingly.)*

## Technical Context

**Language/Version**: Java 17 / Spring Boot 3.5 (identity-service, edge-gateway — matches backend); TypeScript
5.5 / React 18 / Vite 5 (frontend, existing scaffold); Bash + Docker Compose v2 + Terraform ≥1.9 (simulation).
**Primary Dependencies**: identity — Spring Web, Spring Security (auth + BCrypt), JWT (`spring-boot-starter-oauth2-resource-server`
for validation / a JWT lib for issuance), Spring Data JPA + Flyway, Postgres. edge — Spring Cloud Gateway
(2025.0.0, as in 008) + a JWT filter + dynamic per-client routing. frontend — react-router-dom, openapi-fetch
(already declared). sim — LocalStack, `tflocal`/Terraform (reuses the 001 AWS module).
**Storage**: identity-service Postgres (own DB/schema: users, clients, credentials-hashed). Each client backend
keeps its own isolated Postgres (feature 008). Identity holds **no** client business data.
**Testing**: identity — JUnit + Testcontainers (auth, JWT, isolation); edge — routing/claim tests (JUnit +
MockWebServer/WebTestClient); frontend — Vitest + Testing Library (login form, auth flow); sim — a smoke script.
**Target Platform**: Linux containers (local dev + CI); production per feature 001 (client-chosen cloud).
**Project Type**: Web app (frontend + backend services) + local infra simulation.
**Performance Goals**: login → own backend < 30s / ≤3 steps (SC-001); sim up < 15 min (SC-004).
**Constraints**: 0 cross-tenant access (SC-002); no plaintext credentials stored/logged (SC-007, FR-010); no
real cloud credentials in the AWS imitation (SC-005); only the frontend is host-exposed.
**Scale/Scope**: 2 demo clients × (feature-008 stack ≈ 8 containers) + identity + edge + frontend + LocalStack.

**Resolved in research.md / clarifications**: JWT signing = **asymmetric** (identity signs, others verify);
session = **httpOnly encrypted (JWE) cookie**, **90-min** lifetime, **company+username+password** login. Still
scoped in research: how far the LocalStack "AWS imitation" goes given Community-edition limits (ECS/RDS are
Pro); multi-Compose-project wiring for two isolated client stacks on one host.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ Spec + 3 clarifications complete; plan precedes code. |
| II. Test-Driven Development | ✅ Red-first for the security-critical paths: auth success/failure, JWT validation, **tenant isolation** (a client-A token rejected by client-B), login-form flow. |
| III. Security & Data Integrity | ✅ **Core of this feature.** BCrypt-hashed passwords, never logged; signed JWT; TLS in prod; per-client data isolation preserved (separate stacks); brute-force throttling; secrets via env/secret store, never in repo. |
| IV. Environment Isolation | ✅ Sim is a local env; each client stack fully isolated (own network/DB/volumes); no shared datastore across clients. |
| V. Observability & Debuggability | ✅ Structured logs on identity + edge (auth outcomes without credentials); health endpoints on both; failed-login + routing decisions logged. |
| VI. Simplicity & YAGNI | ⚠️ Adds identity-service + edge-gateway + first frontend + a heavy 2-client sim. Justified by the multi-tenant requirement; each piece kept minimal (reuse Spring Cloud Gateway; JWT; 2 clients). See Complexity Tracking. |
| VII. Automated Quality Gates | ✅ CI builds frontend + identity + edge and runs their unit/IT tests; the heavy full-sim smoke is a separate, non-blocking job (build/isolation logic is unit/IT-covered without it). |

**Result**: PASS with one tracked complexity item (below).

## Project Structure

### Documentation (this feature)

```text
specs/009-client-login-deploy-sim/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── identity-api.md      # /auth/login, /auth/logout, token shape
│   ├── edge-routing.md      # JWT→client routing + forwarded headers + isolation rules
│   └── frontend-login.md    # login page states + auth/session UX contract
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                      # first real UI: login page + auth context + protected routing shell
├── src/{pages/Login, auth/, api/, routes}   # React + react-router + openapi-fetch
└── (build enabled: Dockerfile/nginx already scaffolded)

backend/
├── identity-service/          # NEW — auth + user/client directory + JWT issuance (Spring Boot, Postgres)
│   └── (users, clients, cloud preference, endpoint mapping; Flyway; BCrypt)
└── edge-gateway/              # NEW — tenant-aware Spring Cloud Gateway: /auth→identity, /api→client by JWT claim

sim/                           # NEW — local multi-client + AWS-imitation simulation
├── docker-compose.edge.yml    # identity + edge-gateway + frontend + localstack (+ shared network)
├── client-overlay.yml         # overlay that runs a feature-008 stack per client, host-ports off
├── sim-up.sh / sim-down.sh    # bring two client projects up on a shared network + the edge project
└── aws-imitation/             # tflocal config reusing infra/terraform (001 AWS module) against LocalStack

infra/terraform/               # reused by sim/aws-imitation for the US4 AWS deployment imitation
```

**Structure Decision**: Two new backend modules (`identity-service`, `edge-gateway`) added to the Gradle
build; the frontend scaffold becomes real (login + routing shell); a new top-level `sim/` orchestrates two
feature-008 stacks (as separate Compose **projects** on a shared network — no duplication of the 008 compose)
plus the edge/identity/frontend and a LocalStack-based AWS imitation reusing the 001 Terraform.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| New `identity-service` + `edge-gateway` (2 components) | Centralized auth + tenant routing across isolated per-client backends is the clarified model (US1/US2) | Per-deployment auth alone can't route a user to the right client from one login page; a single component mixing auth and routing would violate single-responsibility and complicate the isolation proof |
| Two full feature-008 stacks in the sim | The explicit "simulate in docker" ask + isolation must be *physical* to be provable (US3) | One stack with logical tenants wouldn't prove per-client deployment isolation, the whole point of the 001 model |
| LocalStack AWS imitation (US4) | Explicit ask; de-risks the 001 AWS path locally with no cloud spend | Mocking the cloud in tests wouldn't demonstrate a real Terraform-driven AWS deploy path |

## Phase 0 — Research (see research.md)

Resolves: JWT scheme (**asymmetric** — identity signs, edge/backends verify; httpOnly encrypted JWE cookie,
90-min lifetime), edge dynamic routing by `client` claim + forwarded `X-Kita-*` headers, per-client token
rejection at the client gateway, BCrypt + throttling, the two-Compose-project-on-a-shared-network topology, and
the **LocalStack scope** (Community-edition limits →
imitate the supported AWS control-plane/resources via `tflocal` with the local stack as the compute stand-in).

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: identity entities (User, Client, ClientEndpoint/CloudPreference), JWT claim set, session
  lifecycle, and the sim topology (two client projects + edge + identity + frontend + LocalStack).
- **contracts/identity-api.md**: `POST /auth/login`, `POST /auth/logout`, token validation + shape.
- **contracts/edge-routing.md**: how the edge maps a validated `client` claim to a client backend, the
  forwarded identity headers, and the cross-tenant rejection rule.
- **contracts/frontend-login.md**: login page states (idle/loading/error/success), session handling, sign-out.
- **quickstart.md**: bring up the sim, log in as each client, prove isolation, exercise the AWS imitation.
