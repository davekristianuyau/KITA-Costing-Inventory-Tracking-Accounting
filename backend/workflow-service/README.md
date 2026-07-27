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

## Consumer contracts — how drift is caught (018)

The outbound calls in `ports/http/` are verified against the **receivers' real DTO records** (added as
`testImplementation` project deps), not against hand-written doubles. `src/test/java/.../contract/`:

- one `*ContractTest` per call family — the body the adapter actually puts on the wire is bound to the
  receiver's own request record and Bean-Validated (`FAIL_ON_UNKNOWN_PROPERTIES`, so a stale key fails);
- `LifecycleCallsContractTest` pins method+path for the no-body lifecycle calls;
- `PortCoverageGuardTest` fails the build when a `*Port` method has **no** registered contract test —
  adding an unverified call is reported, not silently trusted;
- `FakeContractParityTest` holds the in-memory fakes to the receivers' own constraints, so an isolated
  build cannot pass on input the real service would reject.

Drift is therefore caught three ways, all before a running environment (SC-003):

| Change | What fails |
|---|---|
| Receiver renames/removes a field | the contract test stops compiling (it reads the real record) |
| Caller sends a stale key | binding fails — *Unrecognized field "customerId" … does not bind to `SalesOrderCreateRequest`* |
| New orchestrated call added | `PortCoverageGuardTest` names it as unverified |

Adding a port method? Write its contract test and register it in `PortCoverageGuardTest.VERIFIED_BY`.

## Dependency note (hr-service roles) — ⚠️ unresolved

Authorization needs the acting employee's back-office role tokens. **hr-service does not have them**:
`Employee` stores no roles, `EmployeeResponse` returns none, and hr's own `Role` enum
(`HR_ADMIN`/`PAYROLL_OFFICER`/`MANAGER`/`EMPLOYEE_SELF`) governs HR's own API, not workflow roles. 007
deferred this ("a small hr-service addition") and the fake `HrPort` hid it; 018 surfaced it, because
against real HR every actor resolved inactive + roleless and every governed action failed.

Current behaviour: `active` is derived from HR's real `status` (`ACTIVE`), and roles come from the
explicit `workflow.hr.position-roles` map (`HrPositionRoles`) — **fail-closed**, so an unmapped position
grants nothing. Configure it per deployment:

```yaml
workflow:
  hr:
    position-roles:
      "SALES CLERK": SALES
      "CASHIER": CASHIER
      "WAREHOUSE STAFF": WAREHOUSE_STAFF
      "PURCHASING OFFICER": PROCUREMENT_STAFF
```

Longer term this belongs to **spec 017** (account→employee identity) or a roles field in hr-service —
see the "Open decision" note in `specs/018-secure-service-contracts/tasks.md`.
