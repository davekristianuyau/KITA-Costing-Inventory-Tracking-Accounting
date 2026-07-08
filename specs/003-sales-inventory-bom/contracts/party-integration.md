# Contract: Party (Customer/Supplier) Integration

**Feature**: 003-sales-inventory-bom | **Date**: 2026-07-08

Customer and supplier profiles are owned by a separate **Party master-data service** (Q1=A). This
operations service only stores party **IDs** on orders and receipts and validates them against the
Party service. Defined as a port so this feature can be built and tested before the Party service
exists.

## Port (interface the operations service depends on)

`PartyClient`:
- `validateCustomer(customerRef) → { exists: boolean, active: boolean }`
- `validateSupplier(supplierRef) → { exists: boolean, active: boolean }`

## Expected Party service endpoints (to be provided by that future service)

- `GET /api/party/customers/{id}` → 200 (active/inactive flag) | 404
- `GET /api/party/suppliers/{id}` → 200 | 404

## Behavior

- On **sales order create/confirm**: `validateCustomer` MUST pass (exists + active) or the
  operation is rejected with a clear Problem response (FR-014, SC-005). No order is persisted with
  an invalid customer.
- On **goods receipt**: `validateSupplier` MUST pass or the receipt is rejected.
- Results MAY be short-cached (e.g., seconds–minutes) to reduce chatter; cache MUST NOT mask a
  party becoming inactive for long.
- The operations service stores only the reference ID (+ optionally a cached display name for
  convenience) — never the authoritative party profile.

## Development / testing

- Until the Party service exists, the HTTP adapter targets a configurable base URL
  (`PARTY_SERVICE_URL`); a **stub/fake `PartyClient`** is used in unit/integration tests and an
  optional local dev stub can be enabled by config.
- Contract tests assert: invalid/inactive party → rejection; valid party → success; Party service
  unreachable → the operation fails safe (reject with a clear error, never silently accept).

## Failure modes

- Party service down/timeout → reject the dependent operation with a ret/­retriable error; do not
  create orphaned links.
- Party later deactivated → new operations referencing it are rejected; existing records retain
  their historical reference.
