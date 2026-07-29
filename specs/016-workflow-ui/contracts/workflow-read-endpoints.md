# Contract — Workflow read-only additions (FR-003, FR-004, FR-005)

Three additive, **read-only** changes to `workflow-service`. They project state the service already holds and
change no behaviour: no workflow, pipeline, authorizer, recorder, or store semantics are touched (beyond adding a
listing method to the pending-review port). No schema change, no migration.

All paths are behind the client gateway at `/api/workflow/**` and therefore behind the 009 edge session.

---

## 1. `GET /api/workflow/activity` — add the `outcome` filter (FR-003)

Existing endpoint, one new optional query param.

**Query params**: `actor?`, `action?`, **`outcome?`** *(new)*, `from?`, `to?` (ISO-8601 instants).

`outcome` accepts an `ActivityOutcome`: `SUCCESS` | `REJECTED_NOT_PERMITTED` | `REJECTED_INVALID` |
`FAILED_UNAVAILABLE`. It composes with the other filters and is applied the same way `from`/`to` already are.
An unknown value is a 400 (Spring enum binding), consistent with the existing `action` param.

**Response**: unchanged — `ActivityView[]`, newest first.

```json
[
  {
    "id": "…", "actorEmployeeId": "emp-sales", "action": "TAKE_SALES_ORDER",
    "outcome": "SUCCESS", "reason": null, "targetRef": "sales-order:…",
    "makerEmployeeId": null, "retryCount": 0, "at": "2026-07-22T02:10:04Z"
  }
]
```

**Tests (red first)**: filtering by each outcome returns only matching rows; `outcome` + `action` compose;
no `outcome` behaves exactly as today.

---

## 2. `GET /api/workflow/authorization` — the role→action grants (FR-004)

New read-only controller over `AuthorizationMappingRepository.findAll()`, mapped through the existing
`AuthorizationMapping.toRule()`.

**Query params**: none (the seeded mapping set is small and fully rendered).

**Response** `200`: `AuthorizationRuleView[]`, ordered by `action`, then `kind`, then `role` (stable rendering).

```json
[
  { "action": "CONFIRM_DELIVERY_RECEIPT", "role": "WAREHOUSE_MANAGER", "kind": "CHECKER" },
  { "action": "RAISE_PURCHASE_ORDER",     "role": "PROCUREMENT_STAFF", "kind": "PERFORM" }
]
```

**Tests (red first)**: returns one row per seeded mapping; the maker/checker `kind` is present and distinct;
ordering is deterministic.

---

## 3. `GET /api/workflow/pending-reviews` — the maker-checker queue (FR-005)

New read-only controller over the `PendingReviewStore`, which gains one method:

```java
/** Snapshot of everything currently awaiting a checker (read-only projection). */
List<PendingReview> list();
```

`InMemoryPendingReviewStore` implements it by copying its map values. No other store method changes.

**Query params**: `action?` (optional `BackOfficeAction` narrowing).

**Response** `200`: `PendingReviewView[]`, oldest first (queue order).

```json
[
  {
    "pendingId": "…", "action": "RECORD_DELIVERY_RECEIPT", "makerEmployeeId": "emp-whse",
    "targetRef": "po:…", "stage": null, "createdAt": "2026-07-22T02:12:41Z"
  }
]
```

⚠️ **`payload` is never serialised.** The stored `Object payload` (the captured request replayed on confirm) is
internal, may not be JSON-serialisable, and has no UI use. The view record must list its fields explicitly rather
than returning `PendingReview` directly.

**Tests (red first)**: a recorded-but-unconfirmed receipt appears with its maker and target; confirming it removes
it from the list; the response body contains no `payload` key; `action` narrowing works; empty store ⇒ `[]`.

---

## Non-goals (explicit)

- No write, edit, or delete of authorization mappings — they are viewed only (spec Out of Scope).
- No durability change to the pending store — it stays in-memory and per-instance by design (007 Clarify Q5).
- No new activity is recorded for these reads; they are not governed actions and do not run through the pipeline.
- No change to `BackOfficePipeline`, `ActionAuthorizer`, `ActorResolver`, `ActivityRecorder`, or any `*Workflow`.
