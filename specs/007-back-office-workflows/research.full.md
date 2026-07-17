# Phase 0 Research — Back-Office Workflow Service

All Technical Context items are resolved from the existing codebase, the constitution, and the two
clarify sessions. **No open NEEDS CLARIFICATION.** Decisions D1–D10 are the baseline; D2/D5 are revised
and D11–D15 added for the 2026-07-17 clarifications.

---

## D1. Stack & module shape

- **Decision**: New Gradle module `backend/workflow-service`, Spring Boot 3.5 / Java 17, port **8088**,
  package root `com.kita.workflow`; `include(":workflow-service")` in `settings.gradle.kts`.
  `build.gradle.kts` copied from `procurement-service` (web, data-jpa, validation, actuator, flyway,
  logstash encoder, postgres runtime; testcontainers + Windows Docker-TCP workaround).
- **Rationale**: Every service is this shape; 8088 is the next free port. Consistency (Constitution VI).
- **Alternatives**: Shared `common` library — rejected as premature (each service owns its `common/`).

## D2. Actor identity & authorization — **REVISED (roles from HR)**

- **Decision**: Two layers.
  1. **Identity** — the acting `employeeId` comes from the gateway header `X-Kita-User` (via a copied
     `CallerContext`).
  2. **Validation + roles** — `ActorResolver` calls `HrPort.getEmployee(id)` to confirm the employee is
     **active/not-separated** *and* to obtain the **authoritative role set assigned in HR**. Roles are
     **not** taken from the `X-Kita-Roles` header (self-asserted, untrusted for authz here).
  - **Authorization** — `ActionAuthorizer` (pure) checks the HR roles against `authorization_mapping` for
    the requested `BackOfficeAction`. Deny → `ForbiddenException` (403), no downstream call.
- **Rationale**: Clarify 2026-07-17 — "HR sets the role when registering the employee"; FR-002 now
  resolves roles from the HR source of record. Stronger than header-trust: a separated or role-revoked
  employee fails even with a stale token. Pure authorizer stays DB-free/unit-testable (Constitution II).
- **Alternatives**: Gateway-token roles (original plan) — rejected by the clarification. Both-must-agree —
  rejected as unnecessary complexity once HR is authoritative.

## D3. Authorization mapping storage

- **Decision**: Seeded table `authorization_mapping (action, role, kind)` (Flyway `V2`), where `kind`
  distinguishes a `MAKER` grant from a `CHECKER` grant for review-gated actions. Loaded into
  `ActionAuthorizer` at startup.
- **Rationale**: Spec designates it a persisted entity; a table keeps grants versioned/editable via
  migration without a redeploy. Still trivial.
- **Alternatives**: Static in-code map — acceptable but spec calls it persisted; table chosen.

## D4. Downstream integration — Port + adapter

- **Decision**: One outbound Port per domain service (`HrPort`, `CrmPort`, `OperationsPort`,
  `ProcurementPort`), each with an `@ConditionalOnProperty(...adapter=http)` `RestClient` adapter and a
  default in-memory fake — the `procurement-service` `OperationsPort`/`HttpOperationsAdapter` pattern.
- **Endpoints** (verified against the domain controllers): HR `GET /api/hr/employees/{id}`; CRM
  `GET/POST/PATCH /api/crm/customers`; Operations `/api/operations/sales-orders`
  (create,`/{id}/items`,`/{id}/confirm`,`/{id}/cancel`,`/{id}/fulfill`), `/api/operations/builds`,
  `/api/operations/items/{id}/availability`; Procurement `/api/procurement/suppliers`,
  `/api/procurement/purchase-orders` (create,`/{id}/approve`,`/{id}/send`,`/{id}/receipts`).
- **Rationale**: Fakes keep the module buildable/unit-testable with no network; http profile wires real
  services per environment.

## D5. Business-level atomicity & compensation — **REVISED (review gates + transient state)**

- **Decision**: Order downstream steps and compensate on failure; no 2PC.
  - **Sales order (US2)**: `SalesOrderWorkflow` drives the lifecycle. DRAFT = create order + add lines +
    reserve in operations (durable anchor). Each subsequent gate (payment-confirmed, released via
    operations `fulfill`, completed) is a separate authorized, attributed transition. If a step fails,
    **cancel** the operations order (compensation). The **operations order is the system of record**; the
    review position is transient (D14) and rebuildable.
  - **Receiving (US4)**: two steps. (a) *record* → a `PendingReview` item held transiently, **no**
    downstream write yet. (b) *confirm* by a distinct checker → a single `ProcurementPort.receive` call,
    which atomically advances the PO **and** posts the goods receipt to operations (built idempotently in
    spec 006). workflow-service does not re-own the two-write dance.
  - **Build (US5)**: one `OperationsPort.build` call (operations atomically explodes BOM, consumes,
    raises finished stock; short → reject whole). No compensation.
- **Rationale**: Strongest atomicity stays where writes are colocated (operations build; procurement
  receipt→ops). workflow-service only compensates its one multi-call, single-owner sequence (sales
  order). Simplest correct design; no 2PC (spec Assumptions; Constitution VI).
- **Alternatives**: Generic saga framework — rejected (YAGNI). 2PC — rejected (unsupported; spec forbids).

## D6. Idempotency & retry safety → see D15

## D7. Error taxonomy (FR-018, SC-006)

- **Decision**: `GlobalExceptionHandler` maps: `ValidationException`→**422** (invalid input, unknown/
  inactive party, oversell, over-receipt, short components, self-review, separated actor);
  `ForbiddenException`→**403** (role not permitted); `DownstreamUnavailableException`→**503** (downstream
  unreachable/5xx after retries — retryable). Every terminal outcome is written to the activity log.
- **Rationale**: SC-006/FR-018 require the employee to see which failure occurred, never a silent partial.

## D8. Exact money & quantities (FR-020)

- **Decision**: All amounts/quantities `BigDecimal`; `common/Money` half-up-to-cents where totals are
  computed; no `double`/`float` on the wire or in persistence.
- **Rationale**: Constitution III + FR-020; identical to the other services' `Money`.

## D9. Testing strategy (Constitution II)

- **Decision**: **Pure unit tests** (no DB/HTTP, fake ports) for `ActionAuthorizer` (permit/deny incl.
  maker vs checker), `ActorResolver` (active + role resolution), each `*Workflow` (lifecycle order,
  self-review refusal, oversell, over-receipt, short components, compensation), and `RetryingCaller`
  (retry then succeed / then fail, no duplicate effect). **Testcontainers IT** for the append-only log +
  seeded mapping. **Adapter tests** vs. a stub server (header propagation, idempotency key, 409-as-applied,
  5xx→retry). **OpenAPI contract test**.
- **Local caveat**: Testcontainers ITs need Docker Desktop TCP 2375 (Windows, currently OFF) — unit tests
  run locally, full suite in CI (Linux). Same as the other services.

## D10. Observability (Constitution V)

- **Decision**: logstash JSON encoder; every action logs actor, action, downstream refs, retry count, and
  outcome, PII/secret-scrubbed before log/`detail`. `/actuator/health` for health-gated deploy.

---

## D11. HR-authoritative role resolution — **NEW**

- **Decision**: `HrPort.getEmployee(id)` returns `EmployeeView { UUID id; boolean active; Set<String> roles }`.
  workflow-service maps the role strings to its `Role` tokens. Result is used for the whole request (one
  HR call per action; no long-lived cache — SMB scale makes per-action lookup fine).
- **Rationale**: Clarify — roles assigned in HR at registration (D2). Keeps a single source of truth.
- **Dependency/risk**: hr-service must expose an employee's assigned back-office role tokens. If the field
  isn't present yet, it's a small hr-service addition (or seed); the **fake** `HrPort` supplies roles so
  workflow-service is testable in isolation regardless. Flagged in plan Dependencies.
- **Alternatives**: Derive roles from job title/department — rejected (indirect, brittle). Cache roles in
  workflow-service — rejected (would duplicate HR master, violating FR-017).

## D12. Maker–checker separation — **NEW (FR-021, SC-009)**

- **Decision**: For review-gated actions (goods-receipt confirmation; sales-order payment confirmation and
  release), the `PendingReview` item records the **maker's `employeeId`**. The confirming request must
  come from a **different `employeeId`** holding the **checker** role (per `authorization_mapping` `kind=CHECKER`).
  Same employee → `ValidationException` "self-review not allowed" (422); wrong role → `ForbiddenException`
  (403). No irreversible effect until the checker confirms.
- **Rationale**: The user's operational model (stockman records, warehouse/branch manager verifies;
  cashier/manager confirms sales payment). Enforced as a pure check in the workflow (unit-testable).
- **Alternatives**: Same-role-different-person only — rejected; the clarification says the roles are
  distinct (maker role ≠ checker role), which also implies different persons.

## D13. Sales-order reviewed lifecycle — **NEW (FR-004, SC-001)**

- **Decision**: States **DRAFT → PAYMENT-CONFIRMED → RELEASED → COMPLETED**, each an authorized,
  attributed transition:
  | Transition | Action / role | Downstream effect |
  |---|---|---|
  | create → DRAFT | `TAKE_SALES_ORDER` / SALES | operations create + add lines + `confirm` (reserve stock) |
  | → PAYMENT-CONFIRMED | `CONFIRM_SALES_PAYMENT` / CASHIER, SALES_MANAGER (≠ maker) | mark paid (transient position + activity log) |
  | → RELEASED | `RELEASE_SALES_ORDER` / WAREHOUSE_STAFF, SALES_MANAGER (packed check) | operations `fulfill` (commit stock) |
  | → COMPLETED | `COMPLETE_SALES_ORDER` / SALES, CASHIER | close; clear transient position |
- **Rationale**: Directly from the clarification ("draft until payment confirmed by manager/cashier;
  release after packed check; completed once handed to customer"). Reservation lives durably in
  operations from DRAFT; the review position is transient (D14).
- **Alternatives**: One-shot create+confirm (original plan) — rejected by the clarification. Adding all 4
  statuses to operations-service — rejected for now (avoids changing implemented spec 003; gates without a
  distinct operations status are carried transiently + logged). Revisit if a durable operations status is
  wanted.

## D14. Transient in-flight review state — **NEW (Clarify Q5)**

- **Decision**: `PendingReviewStore` port with a default **`InMemoryPendingReviewStore`** (a
  `ConcurrentHashMap` keyed by pending-id) holds: pending goods receipts (not yet confirmed) and the sales
  review position. On the checker's confirmation, the durable write is made downstream and the item is
  **removed** from the store. The store is **derived/working state** — reconstructable from the domain
  services (records in the relevant status) and the activity log — so its loss causes at most re-work,
  never a lost or half-applied domain effect.
- **Rationale**: The user's model — "goes on a queue/cache first; once the state updates the next
  downstream service receives it; once complete, clear it from queue/cache; final update in the DB." Keeps
  the two durable stores + FR-017 intact (transient ≠ persisted master). A plain in-memory map is the
  simplest thing that satisfies it at SMB scale (Constitution VI); the **port lets a Redis/queue adapter
  replace it later** with no caller change, exactly like the http/fake port split.
- **Boundary**: The *pending, un-confirmed* action is the losable part. The DRAFT sales order + its
  reservation are an intentional, attributed, **durable** step in operations (not losable) — recoverable
  by querying operations if the transient position is lost.
- **Alternatives**: A third durable `pending_approval` table — rejected (the clarification chose transient;
  a table would edge toward owning workflow state). Durable queue infra now — rejected (YAGNI; interface
  preserves the option).

## D15. Bounded idempotent retry — **NEW (FR-018, SC-010)**

- **Decision**: `RetryingCaller` wraps downstream calls: on a transient failure (timeout / connect error /
  HTTP 5xx) it retries up to `workflow.retry.max-attempts` (default 3) with short backoff, carrying a
  stable **`X-Idempotency-Key`** so a replay is de-duplicated downstream (operations/procurement already
  treat a repeated key / `409` as already-applied). Exhausted → `DownstreamUnavailableException` (503).
  4xx business rejections are **not** retried (surface as 422/403 immediately).
- **Rationale**: The clarification chose auto-retry-then-report. Idempotency keys make retries safe
  (no duplicate side effect — SC-010). A tiny helper avoids adding spring-retry (YAGNI); it's pure and
  unit-testable.
- **Alternatives**: spring-retry dependency — rejected (a bounded loop suffices). No retry / report-only
  (original plan) — rejected by the clarification.
