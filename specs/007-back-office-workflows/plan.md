# Implementation Plan: Back-Office Workflow Service

**Branch**: `007-back-office-workflows` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/007-back-office-workflows/spec.md`
**Clarifications applied**: Session 2026-07-17 (role source = HR; maker–checker review; sales-order
lifecycle; bounded auto-retry; transient in-flight review state).

## Summary

`workflow-service` is a **thin orchestration** microservice: the attributed, authorized entry point a
staff member uses to run the business — draft a sales order and carry it through its reviewed lifecycle,
raise a purchase order, receive a delivery under maker–checker review, build a product, and maintain
customer/supplier records. It **persists only two durable stores**: an append-only **activity log**
(who did what, when, outcome) and a seeded **authorization mapping** (role → action, incl. maker vs.
checker). All master data is sourced live from the domain services (operations, hr, crm, procurement).

The five clarifications shape the design: (1) the acting employee's **roles are authoritative from HR**,
not the gateway token; (2) higher-risk actions enforce **maker–checker separation**; (3) a sales order
runs a **DRAFT → PAYMENT-CONFIRMED → RELEASED → COMPLETED** lifecycle; (4) transient downstream failures
are **retried a bounded number of times** (idempotently) before reporting unavailable; (5) an action
**pending review is held as transient in-flight state**, committed to the owning domain service only on
the checker's confirmation, then discarded — so the two durable stores and FR-017 stay intact.

Technical approach reuses the proven house patterns (Port + HTTP-adapter / in-memory-fake as in
`procurement-service`; `CallerContext`, `common/Money`, append-only `AuditEvent`, Flyway,
Testcontainers) and adds three small, spec-driven mechanisms: an **HR role resolution** step, a
**`PendingReviewStore`** (in-memory port; a cache/queue adapter can replace it later), and a bounded
**`RetryingCaller`** with idempotency keys.

## Technical Context

**Language/Version**: Java 17 (Spring Boot 3.5), Gradle wrapper 8.10.2
**Primary Dependencies**: spring-boot-starter-web, -data-jpa, -validation, -actuator; Flyway
(core + database-postgresql); `RestClient` for downstream calls; logstash-logback-encoder. Test:
spring-boot-starter-test, spring-boot-testcontainers, testcontainers/postgresql, WireMock/MockWebServer.
No new infra dependency for the pending store or retry (in-process; interfaces allow later swap).
**Storage**: PostgreSQL (per-cloud), schema-in-public via Flyway. **Two durable tables**:
`back_office_activity` (append-only) and `authorization_mapping` (seeded). Pending-review state is
**transient/in-process**, not a table.
**Testing**: JUnit 5. Pure unit tests (no DB/HTTP, fake ports) for `ActionAuthorizer`, each `*Workflow`
(incl. maker–checker refusal, lifecycle transitions, oversell/over-receipt/short-components), and
`RetryingCaller`; Testcontainers ITs for the activity log + seeded mapping; adapter tests vs. a stub
server (header propagation, idempotency, 409/5xx→retry); OpenAPI contract test.
**Target Platform**: Linux container behind Spring Cloud Gateway; private.
**Project Type**: Backend microservice (`backend/workflow-service/`, port **8088**).
**Performance Goals**: Interactive SMB scale — tens of concurrent staff; not high-volume batch.
**Constraints**: Exact decimal money/qty, no float (FR-020). Business-level atomicity via ordering +
compensation, **not** 2PC (FR-016). Maker ≠ checker (FR-021). Bounded idempotent retry (FR-018).
Durable financial/inventory writes happen in the domain services on confirmation; the activity log
durably audits every transition; transient state holds no financial truth (Constitution III).
**Scale/Scope**: 6 user stories, 21 FRs, 4 downstream ports + 1 pending-store port, ~12 orchestrated
transitions (incl. the 4-stage sales lifecycle and the 2-step receiving flow).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan satisfies it |
|---|---|---|
| I. Specification-Driven | ✅ | Spec + 2 clarify sessions precede this plan; prioritized P1/P2 stories, Given-When-Then acceptance. |
| II. TDD (NON-NEGOTIABLE) | ✅ | Failing tests first per slice. Authorizer, each workflow (incl. maker–checker refusal, lifecycle order, oversell/over-receipt/short edges), and `RetryingCaller` are **pure, DB-free** unit tests with fake ports; OpenAPI contract test; Testcontainers IT for the log. |
| III. Security & Data Integrity (NON-NEGOTIABLE) | ✅ | Attribution is the service's purpose: append-only activity log records every transition with actor+timestamp+outcome (FR-003). Roles resolved from HR, not self-asserted (FR-002). Maker–checker separation on high-risk actions (FR-021). Exact decimal (FR-020). Durable financial writes are atomic in the domain services on confirmation; transient store carries no financial truth, and its loss yields no half-applied effect. Idempotent retry never duplicates effects (FR-018). |
| IV. Environment Isolation | ✅ | Env-scoped config (`DATABASE_URL`, `*_BASE_URL`, `*.adapter`); no hard-coded endpoints; no shared prod data. |
| V. Observability | ✅ | Structured JSON logs (actor/action/refs/outcome/retry-count); `/actuator/health`; failures log downstream ids + status. |
| VI. Simplicity & YAGNI | ✅ | Reuses existing patterns; no master-data duplication; **receiving's cross-service consistency stays delegated to procurement-service**; sales lifecycle gates are fixed role steps, **not** a configurable engine; pending store is a plain in-memory map behind a port; retry is a small helper — no spring-retry/queue infra added. Four downstream ports are intrinsic to a connective-tissue service. |
| VII. Automated Quality Gates | ✅ | `:workflow-service:build` runs tests + Spotless/Checkstyle; ITs in CI (Linux); no merge on red. |

**Result: PASS.** No violations → Complexity Tracking empty. The three new mechanisms
(`PendingReviewStore`, `RetryingCaller`, HR role resolution) are the *minimum* required by the
clarified FRs, each behind a small interface, so none constitutes speculative complexity.

## Key design decisions (from the clarifications)

1. **Roles authoritative from HR** — identity (`employeeId`) comes from the gateway header `X-Kita-User`;
   **roles come from `HrPort.getEmployee(id).roles`**, not `X-Kita-Roles`. `ActionAuthorizer` checks the
   HR-sourced roles against `authorization_mapping`. (HR must expose an employee's assigned role tokens —
   see Dependencies; the fake supplies them for isolated tests.)
2. **Maker–checker** — the maker's `employeeId` is captured on the pending item; the confirming call must
   come from a **different** `employeeId` holding the checker role, else refused (FR-021, SC-009).
3. **Sales lifecycle** — `SalesOrderWorkflow` drives DRAFT (create+lines+reserve in operations) →
   PAYMENT-CONFIRMED (cashier/manager) → RELEASED (packed check) → COMPLETED (operations fulfill). Each
   transition is one activity-log record. The **operations order is the durable anchor**; the review
   position is transient and rebuildable.
4. **Transient in-flight state** — `PendingReviewStore` (in-memory port) holds pending receipts and the
   sales review position. On confirm, the durable write goes to the domain service and the item is
   cleared. Loss ⇒ at most re-work, never a half-applied domain effect. A distributed cache/queue adapter
   can replace the in-memory one via the same interface, no caller change.
5. **Bounded idempotent retry** — `RetryingCaller` retries transient failures (timeout/5xx) N times with
   backoff, carrying an `X-Idempotency-Key`; still-failing → `DownstreamUnavailableException` (503).

## Project Structure

### Documentation (this feature)

```text
specs/007-back-office-workflows/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions & rationale (updated for the clarifications)
├── data-model.md        # Phase 1 — 2 durable tables + transient state + referenced records
├── quickstart.md        # Phase 1 — runnable validation scenarios (lifecycle + maker–checker)
├── contracts/
│   ├── workflow-api.md       # exposed API (lifecycle + review endpoints)
│   └── downstream-ports.md   # hr/crm/operations/procurement ports + pending-store + retry contract
└── tasks.md             # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

New Gradle module `backend/workflow-service/` (add `include(":workflow-service")` to
`backend/settings.gradle.kts`), following the `procurement-service` layout:

```text
backend/workflow-service/
├── build.gradle.kts                 # mirrors procurement-service (web/jpa/validation/actuator/flyway + testcontainers)
├── src/main/resources/
│   ├── application.yml              # port 8088; workflow.security.stub; *.base-url; *.adapter=fake|http; retry.max-attempts
│   └── db/migration/
│       ├── V1__activity_log.sql
│       └── V2__authorization_mapping.sql   # role→action seed incl. maker/checker
└── src/main/java/com/kita/workflow/
    ├── common/
    │   ├── Money.java  ForbiddenException.java(403)  ValidationException.java(422)
    │   ├── DownstreamUnavailableException.java(503)
    │   ├── RetryingCaller.java      # bounded idempotent retry helper  [UNIT-TESTED]
    │   └── security/
    │       ├── Role.java            # union of HR-forwarded back-office role tokens
    │       └── CallerContext.java   # reads X-Kita-User (employee id); roles come from HR, not the header
    ├── authorization/
    │   ├── AuthorizationMapping.java / …Repository.java   # seeded role→action (+maker/checker) table
    │   ├── BackOfficeAction.java    # enum incl. lifecycle + maker/checker actions
    │   └── ActionAuthorizer.java    # pure: (hrRoles, action) → permit/deny  [UNIT-TESTED]
    ├── activity/
    │   ├── ActivityRecord.java / …Repository.java        # append-only log entity
    │   └── ActivityRecorder.java    # writes every transition outcome (PII-scrubbed)
    ├── actor/
    │   └── ActorResolver.java       # HrPort → validate active + resolve roles (FR-001/002)  [UNIT-TESTED w/ fake]
    ├── pending/
    │   ├── PendingReview.java        # transient item: maker id, action, target, payload, stage
    │   ├── PendingReviewStore.java   # port (in-memory default; cache/queue adapter later)
    │   └── InMemoryPendingReviewStore.java
    ├── ports/                       # outbound port interfaces + DTOs (no master data stored)
    │   ├── HrPort.java              # getEmployee(id) → {active, roles}
    │   ├── CrmPort.java  OperationsPort.java  ProcurementPort.java
    │   ├── http/ Http{Hr,Crm,Operations,Procurement}Adapter.java   # forward X-Kita-*, X-Idempotency-Key; via RetryingCaller
    │   └── fake/ InMemory{Hr,Crm,Operations,Procurement}Adapter.java
    ├── workflow/                    # orchestrations (ordered steps + compensation)  [UNIT-TESTED]
    │   ├── SalesOrderWorkflow.java  # US2: draft→payment→release→complete; cancel-on-failure; maker≠payment-confirmer
    │   ├── PurchaseOrderWorkflow.java # US3: create→approve→send
    │   ├── ReceivingWorkflow.java   # US4: record(pending) → confirm(maker≠checker) → procurement.receive
    │   ├── BuildWorkflow.java       # US5: operations build
    │   └── PartyWorkflow.java       # US6: create/update customer & supplier
    └── api/
        ├── SalesOrderController.java   PurchaseOrderController.java
        ├── ReceivingController.java    BuildController.java
        ├── PartyController.java        ActivityController.java
        └── GlobalExceptionHandler.java # 403 / 422 / 503 taxonomy

src/test/java/com/kita/workflow/     # unit (authorizer, actor-resolver, each *Workflow, RetryingCaller),
                                      # integration (activity-log Testcontainers), adapter (stub HTTP + retry)
```

**Structure Decision**: Backend microservice, one module under `backend/` (one-module-per-service rule).
Package-by-feature with an explicit `ports/` boundary (`http`/`fake` adapters) exactly as
`procurement-service` isolates `operations/`, plus a `pending/` package for the transient store. No
frontend in scope. `Money`, `CallerContext`, exceptions copied per the existing per-service convention
(no premature shared library — YAGNI).

## Complexity Tracking

> No Constitution violations. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Dependencies & risks (flagged for planning/implementation)

- **HR must expose employee role assignments.** `HrPort.getEmployee` needs `{active, roles}`. hr-service
  currently exposes employee status but role tokens for back-office actions may need a small addition (or
  seed). Tracked as a dependency; the fake adapter supplies roles so workflow-service builds/tests in
  isolation regardless. *(Resolve during `/speckit-tasks`: confirm the HR field or add it.)*
- **Sales-order lifecycle vs. operations' current statuses.** operations exposes create/`items`/`confirm`/
  `cancel`/`fulfill`. The 4 review gates map onto these where present; gates without a distinct operations
  status are carried as transient position + durable activity-log records. No change to spec 003 assumed;
  revisit if a durable operations status is later desired.
- **Activity-log retention** (the one low-impact `/speckit-clarify` deferral) — default: retain
  indefinitely (append-only audit); revisit if a purge policy is required. Not blocking.
