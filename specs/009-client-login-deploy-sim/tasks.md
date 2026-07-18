---
description: "Task list for 009-client-login-deploy-sim"
---

# Tasks: Client Login & Per-Client Deployment Simulation

**Input**: Design documents from `/specs/009-client-login-deploy-sim/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — constitution mandates TDD, and this feature is security-critical (authentication +
tenant isolation). Test tasks are written first within each story and MUST fail before implementation.

**Organization**: By user story. New modules: `backend/identity-service`, `backend/edge-gateway`; the frontend
scaffold becomes real; `sim/` orchestrates two feature-008 stacks + edge/identity/frontend + LocalStack.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Compiling skeletons for the two new services + the real frontend shell.

- [X] T001 Add `:identity-service` and `:edge-gateway` to `backend/settings.gradle.kts`
- [X] T002 [P] Create `backend/identity-service` module: `build.gradle.kts` (web, security, data-jpa, flyway, postgres, `com.nimbusds:nimbus-jose-jwt` for JWS/JWE), `IdentityServiceApplication`, `application.yml` (port 8090)
- [X] T003 [P] Create `backend/edge-gateway` module: `build.gradle.kts` (Spring Cloud Gateway `2025.0.0` + `nimbus-jose-jwt` for verify/decrypt), `EdgeGatewayApplication`, `application.yml`
- [X] T004 [P] Enable the frontend build: install deps, `frontend/src/main.tsx` + `App.tsx` + router shell (`/login` + a protected route placeholder); wire `frontend/Dockerfile` to `npm ci && npm run build`
- [X] T005 [P] `.env.example`: RSA signing keypair (identity **private**, shared **public**), JWE encryption key, demo company/user seed values, and edge/identity/service URLs — placeholders only (no real keys committed); document key generation

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Identity data + the asymmetric token issue/verify primitives every story needs.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 identity Flyway `V1__identity.sql`: `client` (id, name, cloud_preference, backend_endpoint, active) and `app_user` (id, client_id FK, username, password_hash, active, failed_attempts, locked_until) with **username unique per client**
- [X] T007 [P] identity entities `Client` + `AppUser` + repositories (`findByClientCompanyAndUsername`)
- [X] T008 [P] identity `TokenService`: sign a JWT **RS256** (private key) with claims `{sub, client, roles, iat, exp(90m), jti}`, then **JWE-encrypt** it; load keys from env (data-model.md)
- [X] T009 [P] identity seed `V2__seed_demo.sql` (or a seeder): two demo clients — `client-a` (cloud_preference AWS) and `client-b` — each with one BCrypt-hashed user
- [X] T010 [P] edge `SessionTokenVerifier`: read the httpOnly cookie, JWE-decrypt, verify RS256 signature (public key) + `exp`, extract `client`/`sub` — shared by the edge filter (and reused by the per-client gateway in US2)

**Checkpoint**: identity persists users/clients and can mint/verify an encrypted, asymmetrically-signed 90-min token.

---

## Phase 3: User Story 1 - Log in and reach my client's backend (Priority: P1) 🎯 MVP

**Goal**: A user submits company + username + password, is authenticated by identity, and the frontend reaches
that user's client backend (one client wired) and reads its data; sign-out works.

**Independent Test**: With identity + edge + one client-008 stack up, valid login → land on the client backend
and read data; invalid → clear error; sign out → session no longer works.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T011 [P] [US1] identity IT `AuthControllerIT` (Testcontainers Postgres): `POST /auth/login` valid → 200 + Set-Cookie (httpOnly) + resolved client; invalid/unknown → 401 (non-revealing); locked → 423 (contracts/identity-api.md)
- [X] T012 [P] [US1] frontend `Login.test.tsx` (Vitest + Testing Library): idle→submitting→success/error states; generic error on 401; no token in JS-readable storage (contracts/frontend-login.md)

### Implementation for User Story 1

- [X] T013 [US1] identity `AuthController`: `POST /auth/login` (company+username+password → BCrypt verify → issue JWE cookie via TokenService), `POST /auth/logout` (clear cookie / revoke jti), per-account throttling (FR-009)
- [X] T014 [US1] edge routing: `/auth/**` → identity-service; `/api/**` → the client backend guarded by `SessionTokenVerifier` — on success **strip** inbound `X-Kita-*` and set trusted `X-Kita-User`/`X-Kita-Client` from the token; 401 when missing/invalid (contracts/edge-routing.md)
- [X] T015 [P] [US1] frontend `pages/Login.tsx` + `auth/` context + protected-route wrapper + sign-out; `api/` openapi-fetch client that relies on the session cookie; redirect on success, generic error on failure (contracts/frontend-login.md)
- [ ] T016 [US1] Wire one client backend (a feature-008 stack) reachable through the edge and confirm the end-to-end login → routed `/api` read works

**Checkpoint**: MVP — a user logs in and works against their own client backend behind the edge.

---

## Phase 4: User Story 2 - Tenant isolation (Priority: P2)

**Goal**: Two clients; each user reaches only their own backend; cross-tenant access and token forgery are
refused — enforced at the edge and again at each client backend.

**Independent Test**: Log in as client-A and client-B users → each sees only their own data; a client-A token
aimed at client-B is rejected; a token minted with a backend's own key is rejected everywhere.

### Tests for User Story 2 ⚠️ (write first, must fail)

- [ ] T017 [US2] edge routing test (WebTestClient/MockWebServer): a `client=A` token routes only to A's backend; `client=B` never contacted; unreachable backend → 503; missing/expired → 401 (SC-002/006)
- [ ] T018 [US2] per-client gateway test: a token whose `client` ≠ the gateway's configured `CLIENT_ID` is rejected (FR-006)
- [ ] T019 [US2] forgery test (SC-008): a token signed with a client backend's **own** key is rejected by the edge and by every client gateway (asymmetric verify — only identity's key validates)

### Implementation for User Story 2

- [ ] T020 [US2] edge: dynamic routing — resolve the validated `client` claim to `<client>-gateway:8081`; unknown/inactive client → 401; unreachable → 503 (no fallback) (contracts/edge-routing.md)
- [ ] T021 [US2] per-client gateway hardening: add a `CLIENT_ID` env + a filter in `backend/gateway` that verifies the token (public key) and rejects any `client` claim ≠ `CLIENT_ID` (defense-in-depth), reusing the verifier from T010

**Checkpoint**: isolation proven — 0 cross-tenant access, no backend can forge or accept a foreign token.

---

## Phase 5: User Story 3 - Simulate the whole model locally in Docker (Priority: P2)

**Goal**: One command brings up two isolated client stacks + identity + edge + frontend; both clients log in to
their own backends; only the frontend is host-exposed.

**Independent Test**: From clean, run `sim/sim-up.sh` → two client stacks + identity + edge + frontend healthy;
log in as each client's user → correct isolated routing; tear down independently.

### Tests for User Story 3 ⚠️ (write first, must fail)

- [ ] T025 [P] [US3] `sim/sim-smoke.sh`: both client stacks + identity + edge + frontend healthy; login as each client via the edge → own backend; datastores/services private; only frontend host-exposed — fails until T022–T024 land

### Implementation for User Story 3

- [ ] T022 [P] [US3] `sim/client-overlay.yml`: overlay on the feature-008 `docker-compose.yml` that removes the gateway host port and injects `CLIENT_ID` (so the same 008 stack runs per client)
- [ ] T023 [US3] `sim/docker-compose.edge.yml`: identity-service + its Postgres + edge-gateway + frontend on a shared external network; edge attaches to both client networks and routes to `kita-client-a-gateway-1` / `kita-client-b-gateway-1` (research D5)
- [ ] T024 [US3] `sim/sim-up.sh` + `sim/sim-down.sh`: bring up two client projects (`-p kita-client-a` / `-p kita-client-b` with the overlay) on the shared network + the edge project; teardown removes each client's containers + volumes independently (FR-017)

**Checkpoint**: the full multi-client login/routing model runs and is demonstrable locally.

---

## Phase 6: User Story 4 - Imitate a client's AWS production deployment locally (Priority: P3)

**Goal**: For the AWS-preferring client, "deploy" against LocalStack (no real cloud), and the login flow reaches
that locally-imitated deployment.

**Independent Test**: Deploy client-a against LocalStack via `tflocal`; log in as client-a → reaches the
imitated deployment; confirm 0 real cloud credentials used.

### Tests for User Story 4 ⚠️ (write first, must fail)

- [ ] T026 [US4] `sim/aws-imitation/verify.sh`: asserts the client-a Terraform applied against LocalStack (resources exist in LocalStack), used **no** real AWS credentials, and the login flow reaches client-a's imitated deployment (SC-005)

### Implementation for User Story 4

- [ ] T027 [US4] `sim/aws-imitation/`: add a `localstack` service to the sim + `tflocal`/endpoint config reusing the **001 AWS module** for the LocalStack-Community-supported resource subset (networking, S3, Secrets Manager); document ECS/RDS-are-Pro → local 008 stack is the compute/DB stand-in (research D6)
- [ ] T028 [US4] `sim/aws-imitation/deploy.sh <client>`: run `tflocal apply` for the client's AWS deployment against LocalStack with no real credentials; connect it into the login flow so client-a resolves to the imitated deployment

**Checkpoint**: the client-preference → AWS-deploy path runs end-to-end locally with no cloud spend.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T029 [P] Structured logging on identity + edge (auth outcomes + routing decisions, **never** credentials/tokens); health endpoints on both (Constitution V)
- [ ] T030 [P] Security review: grep + review for any plaintext credential/token stored or logged; confirm keys/secrets come only from env, none committed (SC-007, FR-010)
- [ ] T031 [P] Update root `README.md` + `specs/009-client-login-deploy-sim/quickstart.md` with the login + simulation workflow
- [ ] T032 Full build: `frontend` (`npm run build` + tests) + `:identity-service:build` + `:edge-gateway:build` + backend build all green
- [ ] T033 Run `quickstart.md` end-to-end: sim up → login as both clients → prove isolation → exercise the AWS imitation
- [ ] T034 [P] CI: add jobs to build/test frontend + identity + edge; run `sim/sim-smoke.sh` as a separate non-blocking job

---

## Dependencies & Execution Order

- **Setup (P1)** → **Foundational (P2, blocks all stories)** → user stories.
- **US1 (P3)**: after Foundational. MVP.
- **US2 (P4)**: after US1 (extends edge routing + adds per-client verification).
- **US3 (P5)**: after US1 (needs identity+edge+frontend); best after US2 (isolation to demonstrate).
- **US4 (P6)**: after US3 (extends the sim) + reuses the 001 Terraform.
- **Polish (P7)**: after the desired stories.

### Within each story
- Test/verification tasks first (must fail), then implementation.
- identity data/token primitives before the auth endpoints; edge verify before routing; frontend after the auth contract.

### Parallel Opportunities
- Setup: T002/T003/T004/T005 (different modules) in parallel.
- Foundational: T007/T008/T009/T010 in parallel (different files); T006 first (schema).
- US1: T011/T012 (tests) parallel; T015 (frontend) parallel with T013/T014 (backend).
- Different components (identity vs edge vs frontend) are largely parallelizable across a story.

---

## Implementation Strategy

### MVP First (US1)
Setup → Foundational → US1 → **STOP & VALIDATE**: one user logs in (company+username+password), gets an
encrypted 90-min session cookie, and reaches their client backend through the edge.

### Incremental Delivery
US1 (login+one backend) → US2 (isolation, 2 clients) → US3 (full local sim) → US4 (LocalStack AWS imitation).
Each is independently testable and adds value.

---

## Notes
- Security-critical: asymmetric signing (only identity mints tokens), httpOnly + JWE-encrypted cookie, 90-min
  re-auth, per-client claim checks, no plaintext credentials anywhere.
- Reuses feature-008 stacks verbatim per client (separate Compose projects) and the 001 Terraform for the AWS
  imitation — no duplication.
- The full sim is heavy (~20 containers); keep its smoke a separate, non-blocking CI job.
- Commit after each task or logical group; simple messages, no AI attribution.
