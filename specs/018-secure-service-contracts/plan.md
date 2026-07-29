# Implementation Plan: Correct & Secure Service-to-Service Integration

**Branch**: `018-secure-service-contracts` | **Date**: 2026-07-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/018-secure-service-contracts/spec.md`

## Summary

The `workflow-service` (back-office orchestration) calls operations/procurement/crm/hr with request and
response shapes that **drift from what those services accept and return** — they were built against
in-memory fakes and never run against the real services — so every governed action fails at the boundary
(unblocking 016 SC-007 depends on fixing this). And all internal calls are **plaintext HTTP** on a shared
bridge network. This feature (1) corrects each caller to the receivers' real contracts, deriving values no
human supplies; (2) adds **consumer-contract tests that bind against the receivers' real DTOs** so drift
fails the build; and (3) puts **mTLS (Spring Boot 3.5 SSL bundles)** on **all** internal traffic —
every service-to-service HTTP hop *and* the Postgres/Redis connections — with per-service identity
enforcement + **persisted refusal records**, in-place cert rotation, and a cert-free bring-up. The
end-to-end proof runs on the **Floci AWS-imitation** deployment. Scope reflects the 2026-07-23
clarifications (see spec Clarifications + [research.md](./research.md)).

## Technical Context

**Language/Version**: Java 17; Spring Boot 3.5.0 (Gradle wrapper 8.10.2)
**Primary Dependencies**: Spring Web (`RestClient`), Spring Boot SSL bundles + actuator (`management.health.ssl`),
Spring Security (X.509 client-cert filter), Jakarta Bean Validation, Jackson, Flyway (refusal-table
migration), Lettuce SSL (Redis); Testcontainers + MockWebServer (tests); OpenSSL/keytool (dev CA bootstrap)
**Storage**: PostgreSQL per service (schema-per-service). **One new table** — `service_call_refusal` — added
per receiving service (5 backend services) via Flyway; no existing table changes. Connections now TLS
(`sslmode`); Redis TLS.
**Testing**: JUnit 5 pure unit + adapter tests (local); consumer-contract tests binding receiver DTOs;
Testcontainers + `*ApiContractTest` (CI); **e2e governed-action run on the Floci AWS-imitation deployment**
(FR-004/SC-001)
**Target Platform**: Linux containers; local `docker-compose` + the **Floci AWS-imitation** stack
(production-parity); managed cloud + gateway
**Project Type**: Backend microservices (monorepo `backend/`) + compose/Floci/infra glue
**Performance Goals**: transport-only overhead (TLS handshake) — no functional latency budget change;
rotation with **0** failed calls (SC-006)
**Constraints**: FR-010 transport-only (no behaviour/authorization/audit change); FR-011 no committed
secrets; FR-012/SC-008 cert-free bring-up with encryption on; deterministic derived values (FR-002);
`client-auth: want` (not `need`) so no-cert refusals are recordable (research Decision 3)
**Scale/Scope**: 6 services; ~8 corrected call families across 4 receivers; ~2 new test suites (contract +
coverage guard); mTLS wiring + `ServiceIdentityFilter` + `service_call_refusal` table **across all 5
backend services + gateway**; Postgres/Redis TLS; cert-bootstrap. No new business scope.

## Constitution Check

*GATE: must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| I. Specification-Driven | ✅ Spec + this plan + design artifacts precede code; prioritized US1–US4. |
| II. TDD (NON-NEGOTIABLE) | ✅ Drift is fixed test-first: the consumer-contract test for a call is written (red against today's adapter) → adapter corrected → green. mTLS/rotation covered by ITs. |
| III. Security & Data Integrity (NON-NEGOTIABLE) | ✅ **This is a security feature**: encrypt **all** internal traffic incl. datastore links (FR-007), mutual auth + **persisted** refusals (FR-008), no committed secrets (FR-011), decimal money on the wire. No change to atomicity/audit (FR-010). |
| IV. Environment Isolation | ✅ Certs generated per environment; local uses a dev CA, real deployments use the per-`{client}-{env}` secret store. No prod material in dev. |
| V. Observability | ✅ Refusals **persisted + queryable** (FR-008); receiver reasons surfaced (FR-003); cert validity via `management.health.ssl`. |
| VI. Simplicity & YAGNI | ⚠️→✅ The clarified scope (datastore TLS + a `ServiceIdentityFilter` + `service_call_refusal` table replicated across all 5 services) is broader, but every piece is **spec-required** (2026-07-23 clarifications), not gold-plating; the per-service replication follows KITA's existing no-shared-library convention. Still reuses `RemoteCall`, real-DTO contract tests (no Spring Cloud Contract), built-in SSL bundles (no mesh). See Complexity Tracking. |
| VII. Automated Quality Gates | ✅ Contract + coverage-guard tests + existing suites run in CI; drift and unverified calls fail the build. |

**Result: PASS** — the added breadth is requirement-driven; one Complexity Tracking entry recorded.

## Project Structure

### Documentation (this feature)
```text
specs/018-secure-service-contracts/
├── plan.md              # this file
├── research.md          # Phase 0 — decisions 1–5
├── data-model.md        # Phase 1 — Orchestrated Call / Integration Contract / Service Identity / Derived Value
├── quickstart.md        # Phase 1 — validation guide (SC-001..SC-008)
├── contracts/
│   ├── orchestrated-calls.md    # corrected request/response per call
│   └── transport-security.md    # mTLS + rotation + local cert bootstrap
├── checklists/requirements.md   # (from /speckit.specify)
└── tasks.md             # /speckit.tasks — NOT created here
```

### Source Code (repository root)
```text
backend/workflow-service/
├── src/main/java/com/kita/workflow/ports/
│   ├── http/Http{Operations,Procurement,Crm,Hr}Adapter.java  # corrected request/response mapping (US1)
│   ├── http/RemoteCall.java                                  # surface receiver's real reason (FR-003)
│   ├── {Operations,Procurement,Crm}Port.java                 # signatures adjusted to real flow (e.g. drop addSalesOrderLine)
│   └── DerivedValues / resolver(s)                           # ref→UUID, default location, supplierCode (FR-002)
├── src/main/resources/application.yml                        # spring.ssl.bundle.*, RestClient SSL bundle, mTLS profile
└── src/test/java/com/kita/workflow/contract/                 # consumer-contract tests + coverage guard (US2)
        (testImplementation deps on operations/procurement/crm/hr DTO records)

backend/{operations,hr,crm,procurement,workflow}-service/     # each RECEIVING service (US3):
├── src/main/resources/application.yml                        #   server.ssl bundle + client-auth=want, JDBC sslmode
├── …/security/ServiceIdentityFilter.java                     #   verify peer cert vs allowlist, refuse + record
├── …/security/ServiceCallRefusal(entity/repo)                #   persisted refusal
└── src/main/resources/db/migration/V*__service_call_refusal.sql   # Flyway table (per service schema)
backend/gateway/  src/main/resources/application.yml          # client SSL bundle (calls backends over mTLS)

docker-compose.yml, sim/aws-imitation/ + docker/certs/        # cert-bootstrap init (services + Postgres + Redis),
                                                              # Postgres TLS + Redis TLS, mounted bundles (US3/US4/US5)
```

**Structure Decision**: Backend monorepo. US1/US2 change is concentrated in `workflow-service` (caller
corrections + contract tests). US3 is now **cross-service**: every backend service gets the SSL bundle +
`ServiceIdentityFilter` + `service_call_refusal` migration; the gateway gets a client bundle; the
compose/Floci stack gains cert bootstrap + Postgres/Redis TLS. No new module; one new table per receiving
service.

## Phasing (maps to user stories, MVP-first)

- **US1 (P1, MVP)**: correct every orchestrated call + derived values + error taxonomy; e2e proof.
- **US2 (P1)**: consumer-contract tests binding real DTOs + coverage guard + fakes held to contract.
  (US1 and US2 are co-developed test-first — the contract test is the red that drives the US1 fix.)
- **US3 (P2)**: mTLS across all HTTP hops (5 services + gateway) + Postgres/Redis TLS + per-service
  `ServiceIdentityFilter` persisting `service_call_refusal` + cert-bootstrap. Broadest slice.
- **US4 (P3)**: in-place rotation via SSL-bundle reload + validity health.
- **e2e/FR-004**: governed-action proof on the **Floci AWS-imitation** deployment (co-verifies US1+US3).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|-----------|--------------------------------------|
| `ServiceIdentityFilter` + `service_call_refusal` table replicated across all 5 backend services | Clarified FR-008 requires **persisted, queryable** refusals at each receiving service; schema-per-service means each writes to its own schema | A single shared table/module rejected — KITA has **no shared library** (one module per service by convention); a cross-service shared schema would break environment/schema isolation (Constitution IV) |
| `client-auth: want` + app-layer enforcement instead of pure TLS `need` | Only way to **record** a no-cert caller (a `need` handshake drops it before any app code) — the exact probe the user wants logged | Pure `client-auth: need` rejected — encrypts fine but cannot persist the refusal FR-008 now mandates |
| Postgres/Redis TLS added to this feature | Clarified FR-007 = "maximum security"; business data also travels service→datastore | Leaving datastore links plaintext rejected — contradicts the clarified SC-004 |
