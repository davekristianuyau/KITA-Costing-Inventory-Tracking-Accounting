# Data Model — Procurement Service UI

No persistence and **no backend change** — the feature maps existing procurement-service capabilities into
manifest functions. Field names/types mirror `procurement-service`'s DTOs so result rendering matches the wire.
Reuses the full 011–014 shared framework (inputs + id→label + the 014 detail sub-table); **no new framework**.

## Surfaced procurement entities (read/response shapes)

- **Supplier** (`SupplierResponse`): `id`, `supplierCode`, `name`, `email`, `phone`, `address`, `paymentTerms`,
  `deliveryTerms`, `status` (ACTIVE/INACTIVE).
- **Supplier Item** (`SupplierItemResponse`): `id`, `supplierId`, `itemRef`, `supplierPrice`, `leadTimeDays`,
  `minOrderQty`.
- **Supplier History** (`SupplierHistoryResponse`): `itemRef`, `field`, `oldValue`, `newValue`, `actor`,
  `changedAt` — an audit of supplier/item changes.
- **Purchase Order** (`PurchaseOrderResponse`): `id`, `poNo`, `supplierId`, `status`
  (DRAFT/APPROVED/SENT/PARTIALLY_RECEIVED/FULLY_RECEIVED/CLOSED/CANCELLED), `orderTotal`, `approvedBy`,
  `lines[]` = **PO Line** (`id`, `itemRef`, `qtyOrdered`, `agreedPrice`, `qtyReceived`, …).
- **Goods Receipt** (`GoodsReceiptResponse`): `id`, `purchaseOrderId`, `orderStatus`, `lines[]`
  (`itemRef`, `qtyReceived`, `unitCost`).
- **Restock Suggestion** (`RestockSuggestionResponse`): `id`, `supplierId`, `status`, `convertedPoId`,
  `lines[]` (`itemRef`, `suggestedQty`, `onHand`, `targetLevel`, `reason`).

## Request shapes (write forms) — required (`*`) from the DTOs

- **Create supplier** (`CreateSupplierRequest`): `supplierCode*`, `name*`, + optional email/phone/address/
  paymentTerms/deliveryTerms.
- **Update supplier** (`UpdateSupplierRequest`): all optional + `status`.
- **Add supplier item** (`SupplierItemRequest`): `itemRef*`, `supplierPrice*`, `leadTimeDays?`, `minOrderQty?`.
- **Create PO** (`CreatePurchaseOrderRequest`): `poNo?`, `supplierId*`, `lines*[{itemRef*, qtyOrdered*,
  agreedPrice?}]` — the **012 list input**.
- **Record receipt** (`RecordReceiptRequest`): `lines*[{itemRef*, qtyReceived*}]` — the **012 list input**
  (body is `{lines:[…]}`).
- **Generate suggestions**: no body.

## Enums (for `select` inputs)

Only **`SupplierStatus`** (ACTIVE/INACTIVE) is user-selectable (update-supplier). `PurchaseOrderStatus` and
`RestockStatus` are **read-only** in responses — lifecycle is driven by the action endpoints, not a status field.

## Manifest-model additions

**None.** 015 reuses the 012/013/014 `InputField` kinds (`reference`, `list`), `resultRefs`, body building, and
the detail sub-table unchanged. The 015 branch must **sync `main`** (post-012–014-merge) so the shared framework
exists before implementation.

## Notes

- Monetary/decimal values (orderTotal, agreedPrice, supplierPrice, unitCost) are displayed exactly as returned;
  the UI performs no procurement arithmetic.
- Receiving's goods-receipt posting to `operations-service` is done by the **backend** (FR-012); the UI triggers
  it and renders the result. Stock effects are observable in the Operations tab (feature 012).
