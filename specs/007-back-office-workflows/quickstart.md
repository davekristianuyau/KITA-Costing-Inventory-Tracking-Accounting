# Quickstart — validating `workflow-service`

Runnable validation scenarios. Contracts: [workflow-api.md](./contracts/workflow-api.md),
[downstream-ports.md](./contracts/downstream-ports.md). Data model: [data-model.md](./data-model.md).

## Prerequisites

- JDK 17 (`JAVA_HOME` set) and the Gradle wrapper (`backend/gradlew`, 8.10.2).
- `include(":workflow-service")` in `backend/settings.gradle.kts`.
- **Run modes:** *Isolated* (default) uses the in-memory fake ports — fakes supply employee roles,
  customers, suppliers, stock, so every scenario runs self-contained. *Wired*: set `workflow.<x>.adapter=http`
  and the `*_BASE_URL` env vars to the running domain services.
- Testcontainers ITs need Docker Desktop TCP 2375 (Windows, currently OFF) — run pure unit tests locally,
  full suite in CI (Linux).

## Build & test

```bash
cd backend
./gradlew :workflow-service:compileJava :workflow-service:compileTestJava
./gradlew :workflow-service:test --tests '*ActionAuthorizerTest' --tests '*ActorResolverTest' \
          --tests '*SalesOrderWorkflowTest' --tests '*ReceivingWorkflowTest' --tests '*RetryingCallerTest'
./gradlew :workflow-service:build     # full build + Spotless/Checkstyle + ITs (Docker/CI)
```

## Run locally (isolated fakes)

```bash
cd backend && ./gradlew :workflow-service:bootRun     # http://localhost:8088
curl -s localhost:8088/actuator/health                # {"status":"UP"}
```
The dev stub resolves a missing `X-Kita-User` to a stub employee with all roles; to exercise role/maker–
checker rules, pass distinct `X-Kita-User` ids whose fake HR roles differ.

---

## Scenario A — US1 attribution, HR roles & authorization (SC-003, SC-004)

1. Act as an active employee whose **HR role** grants the action → succeeds; appears in the log:
   ```bash
   curl -s "localhost:8088/api/workflow/activity" | jq '.[0]'   # actor, action, outcome=SUCCESS, at
   ```
2. As a **separated** employee (fake HR marks inactive) → `422` "employee not active"; logged `REJECTED_INVALID`.
3. As an employee whose **HR roles** don't grant the action → `403` `REJECTED_NOT_PERMITTED`, no side effect.
   *(Note: authorization uses HR roles, not the `X-Kita-Roles` header.)*

## Scenario B — US2 sales-order lifecycle (SC-001, SC-006, SC-009) 🎯 MVP

```bash
# DRAFT (maker = emp-sales), reserves stock
SO=$(curl -s -X POST localhost:8088/api/workflow/sales-orders -H 'X-Kita-User: emp-sales' \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"<valid>","lines":[{"itemId":"<a>","quantity":"10","unitPrice":"125.00"}]}' | jq -r .salesOrderId)
# PAYMENT-CONFIRMED by a DIFFERENT employee holding cashier/manager role
curl -s -X POST localhost:8088/api/workflow/sales-orders/$SO/confirm-payment -H 'X-Kita-User: emp-cashier'
# RELEASED after packed check, then COMPLETED
curl -s -X POST localhost:8088/api/workflow/sales-orders/$SO/release  -H 'X-Kita-User: emp-whse'
curl -s -X POST localhost:8088/api/workflow/sales-orders/$SO/complete -H 'X-Kita-User: emp-sales'
```
- Each transition returns the new `state` and is attributed to its actor.
- **Self-review**: `confirm-payment` as `emp-sales` (the maker) → `422` "self-review not allowed".
- **Oversell**: a draft line exceeding stock → `422`, no order created (and if a later step fails, the
  order is cancelled — no dangling draft, SC-005).

## Scenario C — US3 → US4 buy, then receive under maker–checker (SC-002, SC-006, SC-009)

```bash
PO=$(curl -s -X POST localhost:8088/api/workflow/purchase-orders -H 'X-Kita-User: emp-proc' \
     -H 'Content-Type: application/json' \
     -d '{"supplierId":"<valid>","lines":[{"itemId":"<a>","quantity":"100","unitCost":"12.34"}]}' | jq -r .purchaseOrderId)
curl -s -X POST localhost:8088/api/workflow/purchase-orders/$PO/approve -H 'X-Kita-User: emp-approver'
curl -s -X POST localhost:8088/api/workflow/purchase-orders/$PO/send    -H 'X-Kita-User: emp-proc'
# record (maker) — pending, no inventory change yet
PR=$(curl -s -X POST localhost:8088/api/workflow/purchase-orders/$PO/receipts -H 'X-Kita-User: emp-whse' \
     -H 'Content-Type: application/json' -d '{"lines":[{"itemId":"<a>","quantityReceived":"40"}]}' | jq -r .pendingReceiptId)
# confirm (checker = DIFFERENT manager) — commits PO advance + inventory increase atomically
curl -s -X POST localhost:8088/api/workflow/receipts/$PR/confirm -H 'X-Kita-User: emp-whse-mgr'
```
- Before confirm: inventory unchanged (pending is transient). After confirm: `PARTIALLY_RECEIVED`,
  inventory up by exactly 40.
- **Self-review**: confirm as `emp-whse` (the maker) → `422`.
- **Over-receipt** (>ordered) on confirm → `422`, neither PO nor inventory change (SC-006).

## Scenario D — US5 build (SC-007)

```bash
curl -s -X POST localhost:8088/api/workflow/builds -H 'X-Kita-User: emp-prod' \
  -H 'Content-Type: application/json' -d '{"itemId":"<finished>","quantity":"5"}'
```
- Sufficient components → `201`, components consumed, finished stock +5. Insufficient → `422`, nothing consumed.

## Scenario E — US6 party maintenance immediately usable (SC-008)

```bash
C=$(curl -s -X POST localhost:8088/api/workflow/customers -H 'X-Kita-User: emp-crm' \
    -H 'Content-Type: application/json' -d '{"name":"Acme","active":true}' | jq -r .customerId)
curl -s -X POST localhost:8088/api/workflow/sales-orders -H 'X-Kita-User: emp-sales' \
  -H 'Content-Type: application/json' -d "{\"customerId\":\"$C\",\"lines\":[{\"itemId\":\"<a>\",\"quantity\":\"1\",\"unitPrice\":\"10.00\"}]}"
```
- The just-created customer is accepted immediately (nothing cached here) — `201`.

## Scenario F — transient unavailability: retry then report (FR-018, SC-010)

Wired profile: make a downstream return 5xx briefly. workflow-service retries up to
`workflow.retry.max-attempts`; if it recovers → success; if not → `503 FAILED_UNAVAILABLE` (not `422`),
nothing half-applied, and the activity log shows `retryCount > 0`. Re-run when healthy → succeeds with **no
duplicate** side effect (idempotency key).

## Scenario G — transient pending state loss (Clarify Q5)

Record a receipt (Scenario C step 4) but **restart** workflow-service before confirming. The pending
receipt is gone (transient), inventory and PO are unchanged → the maker simply re-records. No half-applied
delivery.

---

## Done / acceptance mapping

| Scenario | Proves |
|---|---|
| A | SC-003, SC-004, FR-001/002/003 (HR-sourced roles) |
| B | SC-001, SC-006, SC-009, FR-004 lifecycle + compensation SC-005 |
| C | SC-002, SC-006, SC-009, FR-007..011, FR-021 maker–checker |
| D | SC-007, FR-012/013 |
| E | SC-008, FR-014/015 |
| F | FR-016/018, SC-010 retry-then-report, no duplicate |
| G | Clarify Q5 transient-state loss ⇒ no half-applied effect |
