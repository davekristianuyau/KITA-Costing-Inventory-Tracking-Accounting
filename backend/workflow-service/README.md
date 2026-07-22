# workflow-service

KITA's **back-office workflow layer** (spec 007). The attributed, authorized entry point a staff member
uses to run the business: take a customer sales order through its reviewed lifecycle, raise and receive
purchase orders under maker–checker review, build products, and maintain customer/supplier records.

It is **thin orchestration**: it persists only two durable tables — an append-only activity log and a
seeded authorization mapping — and composes the domain services (hr, crm, operations, procurement). It
never duplicates their master data (FR-017; guarded by `NoDuplicateMastersTest`).

- **Port**: 8088 · **Package**: `com.kita.workflow` · Java 17 / Spring Boot 3.5.
- **Durable tables**: `back_office_activity` (V1), `authorization_mapping` (V2, seeded).
- **Transient**: review positions / pending receipts live in an in-memory `PendingReviewStore` (Clarify
  Q5) — losing them means the maker re-records, never a half-applied effect.

## Key rules

- **Identity** comes from the gateway header `X-Kita-User`; **roles are resolved from HR**, not the
  header (FR-002). `ActionAuthorizer` checks HR roles against `authorization_mapping`.
- **Maker–checker**: goods-receipt confirmation and sales-order payment confirmation/release must be
  performed by a *distinct* employee holding the checker role (FR-021); self-review → 422.
- **Error taxonomy**: 403 `REJECTED_NOT_PERMITTED`, 422 `REJECTED_INVALID`, 503 `FAILED_UNAVAILABLE`.
- **Money/quantities** are exact `BigDecimal` (FR-020); the PO total is computed half-up via `common/Money`.
- **Bounded idempotent retry**: http adapters call through `RemoteCall`/`RetryingCaller` — a transient
  5xx is retried with a stable `X-Idempotency-Key`; a 409 is treated as already-applied (SC-010).

## Read-only endpoints (feature 016)

Three additive **projections** that back the console's Workflow tab. They record no activity and touch no
workflow, pipeline, authorizer, or recorder path — a read that writes would silently pollute the audit
trail, so `ActivityQueryTest`/`AuthorizationQueryTest` assert `never(save)`.

| Endpoint | Purpose |
|---|---|
| `GET /api/workflow/activity?actor&action&outcome&from&to` | the append-only log; **`outcome`** is the 016 addition |
| `GET /api/workflow/authorization` | the seeded role→action→kind grants, ordered action → kind → role |
| `GET /api/workflow/pending-reviews?action` | what awaits a checker, oldest first |

⚠️ `PendingReviewView` lists its fields explicitly so the store's captured `payload` (replayed on confirm)
can never be serialised to a browser. Do not replace it with the `PendingReview` record itself.

## Run modes

- **Isolated (default)** — every port uses its in-memory fake (`workflow.<x>.adapter=fake`). Fakes seed
  employees (`emp-sales`, `emp-cashier`, `emp-whse-mgr`, …) and an all-roles `stub-admin` for header-less
  dev calls, so the whole service runs self-contained.
- **Wired** — set `workflow.<x>.adapter=http` and `*_BASE_URL` to the running domain services
  (hr 8085, crm 8086, operations 8083, procurement 8087).

```bash
cd backend
./gradlew :workflow-service:compileJava :workflow-service:compileTestJava   # verify baseline
./gradlew :workflow-service:bootRun            # http://localhost:8088 (isolated fakes)
curl -s localhost:8088/actuator/health         # {"status":"UP"}
./gradlew :workflow-service:build              # full build + Spotless/Checkstyle + ITs (Docker/CI)
```

## Testing

Pure unit tests (fake ports, no DB/HTTP) cover `Money`, `ActionAuthorizer`, `ActorResolver`,
`BackOfficePipeline`, every `*Workflow`, `RetryingCaller`, and the http retry path (`RemoteCall` via
MockWebServer) — they run locally. Testcontainers ITs (`ActivityLogIT`) and the `*ApiContractTest`
suites need Docker and run in **CI** (Linux); locally they are skipped unless Docker Desktop's *Expose
daemon on tcp://localhost:2375* toggle is on. See `specs/007-back-office-workflows/quickstart.md`.

## Dependency note (hr-service roles)

Authorization needs an employee's assigned back-office role tokens from
`GET /api/hr/employees/{id}` (`EmployeeView{id, active, roles}`). If hr-service does not yet expose the
`roles` set, that is a small hr-service addition/seed; the **fake `HrPort` supplies roles** so
workflow-service builds and tests in isolation regardless. Tracked in `plan.md` Dependencies.
