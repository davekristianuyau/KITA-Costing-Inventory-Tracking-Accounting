# Phase 1 Data Model — Back-Office Workflow Service

This service **persists only two durable tables**. Review-gated actions add a **transient** in-flight
structure (not persisted as a master). All master data is referenced, never duplicated (FR-017).
Money/quantities are `BigDecimal` (FR-020).

---

## Durable owned entities (persisted)

### 1. `back_office_activity` — append-only activity log (FR-003, SC-003)

The durable "who did what, when, outcome" — one row per **transition** (draft, payment-confirm, release,
complete, record-receipt, confirm-receipt, build, party edit). Append-only: no `UPDATE`/`DELETE`.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `@UuidGenerator` |
| `actor_employee_id` | text, not null | acting employee (`X-Kita-User`, HR-validated) |
| `action` | text, not null | a `BackOfficeAction` value |
| `outcome` | text, not null | `SUCCESS` \| `REJECTED_NOT_PERMITTED` \| `REJECTED_INVALID` \| `FAILED_UNAVAILABLE` |
| `reason` | text, null | required when `outcome != SUCCESS` (scrubbed) |
| `target_ref` | text, null | affected domain record(s): `sales-order:<uuid>`, `po:<uuid>`, `receipt:<uuid>`, `build:<uuid>`, `customer:<uuid>`, `supplier:<uuid>` |
| `maker_employee_id` | text, null | for a checker's confirmation: who made the reviewed record (self-review audit) |
| `idempotency_key` | text, null | key forwarded to downstream posts (retry-safety) |
| `retry_count` | int, not null default 0 | downstream retries performed (observability, SC-010) |
| `at` | timestamptz, not null | server timestamp |

- **Invariants**: append-only; written for **every** terminal outcome (success + both rejections + retry
  exhaustion) so 100% of attempts are attributable. **Also the durable record of every lifecycle/review
  transition**, so the transient position (below) is reconstructable from it.
- **Index**: `(actor_employee_id, at)`, `(action, at)`, `(target_ref)`.

### 2. `authorization_mapping` — role → action grants (FR-002, FR-021, SC-004)

Seeded reference data (Flyway `V2`); the maker/checker split lives here.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `action` | text, not null | a `BackOfficeAction` value |
| `role` | text, not null | a `Role` token (as assigned in HR) |
| `kind` | text, not null | `PERFORM` (normal) \| `MAKER` \| `CHECKER` (review-gated) |
| unique | `(action, role, kind)` | |

- **Seed (V2)** — see the action/role table below. `ActionAuthorizer.permits(hrRoles, action, kind)` =
  any of the caller's HR roles grants that (action, kind).

---

## Transient (in-flight) structure — NOT persisted as a master (Clarify Q5)

### `PendingReview` — held in `PendingReviewStore` (in-memory default)

Working state for a review-gated action awaiting its checker; discarded on confirmation.

| Field | Notes |
|---|---|
| `pendingId` | UUID, the handle returned to the maker |
| `action` | the review-gated `BackOfficeAction` (e.g. `RECORD_DELIVERY_RECEIPT`) |
| `makerEmployeeId` | who created it (enforces maker ≠ checker) |
| `targetRef` | the domain record it concerns (e.g. `po:<uuid>`) |
| `payload` | the captured request (e.g. received lines) to replay downstream on confirm |
| `createdAt` | for optional expiry |

- **Lifecycle**: `record → store.put(pending)` (no downstream write) → `confirm` by a distinct checker →
  downstream durable write → `store.remove(pendingId)`.
- **Durability**: none by design. Loss ⇒ the maker re-records; **no** domain effect occurred, nothing
  half-applied. Reconstructable from the activity log + domain-service status if a durable adapter is
  later substituted (same port).

---

## Enumerations (code, not tables)

### `BackOfficeAction`
`TAKE_SALES_ORDER`, `CONFIRM_SALES_PAYMENT`, `RELEASE_SALES_ORDER`, `COMPLETE_SALES_ORDER`,
`RAISE_PURCHASE_ORDER`, `APPROVE_PURCHASE_ORDER`, `SEND_PURCHASE_ORDER`,
`RECORD_DELIVERY_RECEIPT`, `CONFIRM_DELIVERY_RECEIPT`, `BUILD_PRODUCT`,
`MAINTAIN_CUSTOMER`, `MAINTAIN_SUPPLIER`.

### `Role` (tokens assigned in HR)
`SALES`, `CASHIER`, `SALES_MANAGER`, `WAREHOUSE_STAFF`, `WAREHOUSE_MANAGER`, `PROCUREMENT_STAFF`,
`PROCUREMENT_APPROVER`, `PRODUCTION`, `CRM_ADMIN`.

### Seed: action → role (kind)

| Action | Roles | kind |
|---|---|---|
| `TAKE_SALES_ORDER` | SALES | MAKER |
| `CONFIRM_SALES_PAYMENT` | CASHIER, SALES_MANAGER | CHECKER |
| `RELEASE_SALES_ORDER` | WAREHOUSE_STAFF, SALES_MANAGER | CHECKER |
| `COMPLETE_SALES_ORDER` | SALES, CASHIER | PERFORM |
| `RAISE_PURCHASE_ORDER` | PROCUREMENT_STAFF | PERFORM |
| `APPROVE_PURCHASE_ORDER` | PROCUREMENT_APPROVER | PERFORM |
| `SEND_PURCHASE_ORDER` | PROCUREMENT_STAFF | PERFORM |
| `RECORD_DELIVERY_RECEIPT` | WAREHOUSE_STAFF | MAKER |
| `CONFIRM_DELIVERY_RECEIPT` | WAREHOUSE_MANAGER | CHECKER |
| `BUILD_PRODUCT` | PRODUCTION | PERFORM |
| `MAINTAIN_CUSTOMER` | CRM_ADMIN, SALES | PERFORM |
| `MAINTAIN_SUPPLIER` | PROCUREMENT_STAFF | PERFORM |

### `ActivityOutcome`
`SUCCESS`, `REJECTED_NOT_PERMITTED` (403), `REJECTED_INVALID` (422), `FAILED_UNAVAILABLE` (503).

---

## Referenced records (owned elsewhere — NEVER persisted here)

Only identifiers appear in `back_office_activity.target_ref`.

| Referenced record | Owning service | Port | Used by |
|---|---|---|---|
| Employee (id, active, **roles**) | hr-service | `HrPort` | US1 actor validation + role resolution |
| Customer (id, active) | crm-service | `CrmPort` | US2, US6 |
| Supplier + supplied items | procurement-service | `ProcurementPort` | US3, US6 |
| Item / Inventory availability | operations-service | `OperationsPort` | US2, US5 |
| Sales Order (+ lines, reservation, fulfill) | operations-service | `OperationsPort` | US2 |
| Purchase Order (+ lines, lifecycle) | procurement-service | `ProcurementPort` | US3, US4 |
| Goods Receipt (posts to operations) | procurement-service | `ProcurementPort` | US4 |
| Bill of Materials / Build | operations-service | `OperationsPort` | US5 |

---

## State machines (behavior; durable state lives in the owning service)

### Sales order (US2) — durable anchor in operations; review position transient
```
DRAFT ──confirm payment (cashier/mgr, ≠maker)──▶ PAYMENT-CONFIRMED
      ──release after packed check (whse/mgr)──▶ RELEASED (operations fulfill)
      ──handed to customer──▶ COMPLETED (clear transient position)
```
- DRAFT creates the order + reserves stock in operations (durable). Oversell at draft → 422, no order.
- Any downstream step failure → **cancel** the operations order (compensation); nothing left standing.

### Goods receipt (US4) — maker–checker; pending is transient
```
record (whse staff) ──▶ PENDING-REVIEW (transient, no downstream write)
confirm (whse mgr, ≠maker) ──▶ procurement.receive → PO advances + inventory up (atomic, one call)
```
- Over-receipt refused downstream → 422, neither PO nor inventory change. Self-review → 422.

### Purchase order (US3), Build (US5), Party (US6)
- PO: `create → approve → send` (threshold enforced by procurement). Build: single atomic operations
  call. Party: single create/update; immediately usable next call (SC-008, nothing cached here).
