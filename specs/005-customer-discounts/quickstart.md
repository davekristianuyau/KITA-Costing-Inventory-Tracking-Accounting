# Quickstart: Customer Records & Discounts Service

**Feature**: 005-customer-discounts | **Date**: 2026-07-12

Operator happy path once implemented; doubles as the manual acceptance walkthrough. `crm-service` runs
behind the gateway with its own `crm` PostgreSQL schema.

## 1. Onboard a customer
Create a customer; record a government-mandated entitlement (senior/PWD) with its supporting ID.
Expected: unique `customer_code`, status ACTIVE, entitlement stored (ID ref not shown in logs), history
retained on edits.

## 2. Compute a cascading discount (MVP)
Configure a promotional rule set of ‑25% then ‑5%; compute for a base of 1000. Expected: `finalPrice`
712.50 with a breakdown removing 250.00 (base 1000) then 37.50 (base 750), reconciling exactly.

## 3. Loyalty tier
Define a loyalty tier with a threshold + discount; evaluate a qualifying customer; compute a sale.
Expected: the loyalty discount appears as a cascade tier; a non-qualifying customer gets none.

## 4. Government-mandated discount + stacking
With the seeded senior/PWD rule and a promotional cascade both applicable, compute for an entitled
customer under the default MOST_FAVORABLE policy. Expected: the customer gets the better of statutory
vs. promotional (not both); VAT treatment applied per the rule; a customer lacking the supporting ID
does not get the statutory discount (flag `ENTITLEMENT_WITHHELD`).

## 5. Edge cases
Compute with discounts that would exceed the base → `finalPrice` capped at 0, flag `CAPPED`; compute
for an unknown/walk-in customer → base price, empty breakdown.

## 6. Party integration
Fetch a customer by id via the endpoint operations-service uses to validate a sale's customer.

## Acceptance mapping
| Step | Validates |
|------|-----------|
| 1 | US1, FR-001/002/003/014 |
| 2 | US2, FR-004/005/006/007, SC-001/002 |
| 3 | US3, FR-010/011, SC-004 |
| 4 | US4, FR-012/013/014, SC-003 |
| 5 | Edge cases, FR-008, SC-005 |
| 6 | FR-015 (customer Party master) |
