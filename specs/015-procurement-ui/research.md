# Research — Procurement Service UI

Phase 0 for 015. Grounded by reading `backend/procurement-service/src/main/java/com/kita/procurement/`
(controllers, DTOs, `common/security/CallerContext`).

## D1 — Endpoint inventory (grounded); all needed reads + writes exist → NO backend change

Under `/api/procurement`. **Reads (GET):**

| Function | Method + path | Inputs | Returns |
|---|---|---|---|
| List suppliers | `GET /suppliers` | — | `SupplierResponse[]` |
| Supplier detail | `GET /suppliers/{id}` | id | `SupplierResponse` |
| Supplier items | `GET /suppliers/{id}/items` | id | `SupplierItemResponse[]` |
| Supplier history | `GET /suppliers/{id}/history` | id | `SupplierHistoryResponse[]` |
| List purchase orders | `GET /purchase-orders` | — | `PurchaseOrderResponse[]` (status, orderTotal, lines) |
| PO detail | `GET /purchase-orders/{id}` | id | `PurchaseOrderResponse` (lines[] with qtyOrdered/qtyReceived) |
| PO receipts | `GET /purchase-orders/{id}/receipts` | id | `GoodsReceiptResponse[]` |
| Reorder suggestions | `GET /restock/suggestions` | — | `RestockSuggestionResponse[]` (lines[] with suggestedQty/onHand/targetLevel) |

**Writes (POST/PATCH):** `POST /suppliers`, `PATCH /suppliers/{id}`, `POST /suppliers/{id}/items`,
`POST /purchase-orders`, `POST /purchase-orders/{id}/{approve|send|cancel|close}`,
`POST /purchase-orders/{id}/receipts` (**receiving**), `POST /restock/suggestions` (**generate**, no body),
`POST /restock/suggestions/{id}/{convert|dismiss}`.

**Decision**: every read a user story needs already exists (suppliers list/detail/items/history, POs
list/detail/receipts, restock suggestions), and every write exists. **015 adds NO backend endpoints** (honors the
spec's "no backend change"). Like 014, and unlike 012/013, there is no write-only gap.

## D2 — Receiving posts to operations in the backend (FR-012)

`POST /purchase-orders/{id}/receipts` (`RecordReceiptRequest{lines:[{itemRef, qtyReceived}]}`) records receiving
and **the backend posts the goods receipt to `operations-service`**; the response (`GoodsReceiptResponse`) carries
the updated `orderStatus` + received lines. **Decision**: the UI only triggers receiving and renders the result —
it never calls operations itself. Stock effects are observable in the **Operations** tab (feature 012). Over-receipt
and wrong-state errors surface via 011's error state.

## D3 — Role-gating (stub mode → demo session works)

Every procurement-service endpoint is role-gated (`PROCUREMENT_ADMIN` / `APPROVER` / `RECEIVER`). `CallerContext`
runs in **stub mode by default** (`procurement.security.stub=true`) — a caller with no `X-Kita-Roles` header gets
**all roles**, so the 011/009 console's demo session can exercise Procurement; a real-role deployment without a
role shows a clear **403** (011 error state).

## D4 — Reuse the full 011–014 framework; NO new framework

**Decision**: 015 adds **no framework code**. It reuses:
- the **012** `reference` picker (supplier picker sourced from `GET /suppliers`, value `id`, label
  `supplierCode — name`) and `list` input (PO lines, receipt lines) + `resultRefs` id→label (`supplierId` → the
  supplier label);
- the **013** `bodyInput`/dotted-name body building (not needed here — PO create and receipt bodies are plain
  objects with a `lines` array, which the standard object-body builder produces);
- the **014 detail sub-table** — a PO detail (`GET /{id}`) and a goods receipt return an object with a `lines[]`
  array, which renders as a nested sub-table (already merged). Restock suggestions render as a table (each row a
  suggestion; its nested `lines` show compactly).

## D5 — Statuses are read-only; the one editable enum is supplier status

`PurchaseOrderStatus` (DRAFT/APPROVED/SENT/PARTIALLY_RECEIVED/FULLY_RECEIVED/CLOSED/CANCELLED) and `RestockStatus`
appear only in **responses** (lifecycle is driven by the action endpoints, not a status field). The only editable
enum is **`SupplierStatus`** (ACTIVE/INACTIVE) on update-supplier. **Decision**: lifecycle transitions are the
dedicated action functions (approve/send/cancel/close/receive); only update-supplier has a `status` select.

## Summary of decisions

1. **No backend change** — all reads + writes exist; receiving's cross-service posting is in the backend.
2. **No new frontend framework** — reuses 012 inputs + `resultRefs`, 013 body building, and the 014 detail
   sub-table (PO/receipt `lines[]`).
3. procurement-service is role-gated; stub mode (sim default) → demo session gets all roles; else clear 403.
4. Supplier reference picker + `supplierId` id→label; PO detail lines via the sub-table; suggestions as a table.
5. ⚠️ Sync `main` into 015 first (branch predates the 012–014 merges).
