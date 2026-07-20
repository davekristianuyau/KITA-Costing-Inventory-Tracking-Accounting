---
description: "Task list for 014-crm-ui"
---

# Tasks: CRM Service UI

**Input**: Design documents from `/specs/014-crm-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD. Frontend = Vitest + Testing Library (red-first per story).
**No backend code** — all CRM reads/writes the stories need already exist in `crm-service`.

**Organization**: By user story. **Evolves the 011 `frontend/` reusing the 012/013 shared inputs** (no new input
types) plus **one small generic result enhancement** (detail sub-table for nested arrays — for the quote
breakdown). Contracts: [crm-manifest.md](./contracts/crm-manifest.md) (frontend functions),
[workspace-result-enhancement.md](./contracts/workspace-result-enhancement.md) (detail sub-table). The 012/013
shared inputs must be present — **sync `main` first (T001)**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup

- [X] T001 **Sync `main` into `014-crm-ui`** (`git merge origin/main`) so the 012/013 shared inputs
  (`frontend/src/workspace/inputs/{ReferenceInput,ListInput,FieldInput}.tsx`, `workspace/result/idLabels.ts`),
  the `reference`/`list` `InputField` kinds + `resultRefs`, and the `bodyInput`/dotted-name body building in
  `FunctionWorkspace` are present; resolve any conflicts (keep the 014 CLAUDE.md marker); confirm
  `cd frontend && npm test && npm run build` green
- [X] T002 [P] Create `frontend/src/services/manifests/crm.ts` and point the `crm` entry in `frontend/src/services/registry.ts` at it (migrate the placeholder `customers` function into the module)

---

## Phase 2: Foundational (Shared sources + the quote-breakdown result enhancement)

**⚠️ Blocks the user stories — the reference sources are used everywhere; the detail sub-table is needed by the US2 quote.**

- [X] T003 Define the shared manifest sources in `frontend/src/services/manifests/crm.ts` — `CUSTOMERS_SOURCE` (`/api/crm/customers`, value `id`, label `customerCode — name`), `LOYALTY_TIERS_SOURCE` (`/api/crm/loyalty/tiers`), and `resultRefs` helpers that label `customerId` (from customers) and `loyaltyTierId` (from loyalty tiers) per contracts/crm-manifest.md
- [X] T004 [P] Extend `frontend/tests/Workspace.test.tsx` (red): a `detail` result whose object has an **array-of-objects** field renders a nested **sub-table**; an **array-of-scalars** field renders as a joined list; existing single-level `detail` results render unchanged
- [X] T005 Implement the detail sub-table in `frontend/src/workspace/FunctionWorkspace.tsx` (`DetailView`: array-of-objects → sub-table, array-of-scalars → joined list, empty array → dash) per contracts/workspace-result-enhancement.md

**Checkpoint**: the manifest module + shared sources exist; nested-array detail results (the quote) render cleanly.

---

## Phase 3: User Story 1 - Browse customers and their tiers (Priority: P1) 🎯 MVP

**Goal**: Customer list, customer detail (incl. type + loyalty tier), and per-customer entitlements — read-only, via the edge.

**Independent Test**: Customers → Customers lists customers; Customer detail (pick one) shows attributes incl. its
loyalty tier; Customer entitlements shows any SENIOR/PWD eligibility.

### Frontend (all endpoints already exist — no backend change)

- [X] T006 [P] [US1] Write `frontend/tests/CrmManifest.test.tsx` (red): the `customers`, `customer`, and `entitlements` functions render and run against a mocked edge; `customer` uses the reference picker sourced from `/api/crm/customers`; `customer` detail resolves `loyaltyTierId` to its tier label
- [X] T007 [US1] Add the **Customers** read functions (`customers`, `customer`, `entitlements`) to `frontend/src/services/manifests/crm.ts` per contracts/crm-manifest.md (resultRefs for `customerId`/`loyaltyTierId`)
- [X] T008 [US1] Verify US1: `cd frontend && npm test && npm run build` green

**Checkpoint**: MVP — browse customers, open one, see its tier + entitlements, all through the edge.

---

## Phase 4: User Story 2 - Preview a price quote (Priority: P2)

**Goal**: The cascading/statutory/VAT quote — a customer + sale date + line items → an itemized breakdown.

**Independent Test**: Quote → pick a customer, enter a sale date, add line items → the result renders `baseTotal`,
`finalPrice`, and a **breakdown sub-table** with each discount step (tier + origin + amount removed) + flags.

### Frontend (compute POST already exists — no backend change)

- [X] T009 [P] [US2] Extend `frontend/tests/CrmManifest.test.tsx` (red): `quote` runs a POST to `/api/crm/discounts/compute` with `{customerId, saleDate, lineItems:[{itemRef,quantity,unitPrice}]}` and renders `baseTotal`/`finalPrice` + the `breakdown[]` as a sub-table (mock the edge)
- [X] T010 [US2] Add the **Quote** `quote` function to `crm.ts` (`customerId` reference→customers, `saleDate` text, `lineItems` **list** of {itemRef, quantity, unitPrice}; result `detail`)
- [X] T011 [US2] Verify US2: `cd frontend && npm test && npm run build` green

**Checkpoint**: the headline pricing preview works end-to-end, itemized and rendered verbatim.

---

## Phase 5: User Story 3 - Review discount and statutory rules (Priority: P2)

**Goal**: The configured discount rules, the discount policy (stacking mode), and the loyalty tiers.

**Independent Test**: Discount rules lists the tier definitions; Discount policy shows the stacking mode; Loyalty
tiers lists the loyalty/repeat tier definitions.

### Frontend (all endpoints already exist — no backend change)

- [X] T012 [P] [US3] Extend `frontend/tests/CrmManifest.test.tsx` (red): `discount-rules` (table), `discount-policy` (detail), and `loyalty-tiers` (table) render + run against a mocked edge
- [X] T013 [US3] Add the **Discount rules** read functions (`discount-rules` [optional `asOf`], `discount-policy`, `loyalty-tiers`) to `crm.ts`
- [X] T014 [US3] Verify US3: `cd frontend && npm test && npm run build` green

**Checkpoint**: the pricing rules are reviewable — the input to trusting a quote.

---

## Phase 6: User Story 4 - Manage customers and discount assignments (Priority: P3)

**Goal**: The write actions — create/update a customer, mark senior/PWD eligible, evaluate loyalty, and author the
pricing rules — each a validated form, verifiable via the US1/US3 reads.

**Independent Test**: Create a customer → it appears in Customers; add an entitlement → a quote applies the
mandated discount; evaluate loyalty → the customer's tier updates; create a discount rule → it appears in rules.

### Frontend (writes already exist — no backend change)

- [X] T015 [P] [US4] Extend `frontend/tests/CrmManifest.test.tsx` (red): `create-customer` blocks on missing required inputs then POSTs; `add-entitlement` (SENIOR/PWD); `evaluate-loyalty`; `create-discount-rule` run against the mocked edge
- [X] T016 [US4] Add the **write** functions to `crm.ts` — `create-customer`, `update-customer`, `add-entitlement`, `evaluate-loyalty`, `create-discount-rule`, `set-discount-policy`, `create-loyalty-tier` — reading the enum option lists (CustomerStatus, DiscountOrigin, DiscountComputationKind, StackingMode) and the `LoyaltyTierDto` shape from `crm-service` at this point; use customer reference pickers + enum selects
- [X] T017 [US4] Verify US4: `cd frontend && npm test && npm run build` green; create→list round-trips (customers) work against the mocked edge

**Checkpoint**: the full write surface works and is verifiable via the reads.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T018 [P] Accessibility/responsive pass: the reused inputs stay keyboard-navigable with visible focus, and CRM result shapes (the quote breakdown sub-table + result tables) scroll (not the page) at the 011 768px floor across `frontend/src/`
- [X] T019 [P] Docs: add a CRM note to `frontend/README.md` (the CRM manifest is frontend-only; the quote breakdown renders via the detail sub-table)
- [X] T020 [P] Confirm CI covers the frontend stream: the 011 `frontend` job runs the new `CrmManifest` suite + the extended `Workspace` suite; **no backend job change** (no backend code) — adjust `.github/workflows/ci.yml` only if a gap exists
- [X] T021 Full verification: `cd frontend && npm test && npm run build` green; run `quickstart.md` end-to-end (reads → quote → rules → writes) with 0 real cloud

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003–T005)** → user stories. **T001 (sync main) is a hard prerequisite**
  — the manifest depends on the 012/013 shared inputs; **T005 (detail sub-table)** blocks the US2 quote.
- **US1 (T006–T008)**: after Foundational; frontend-only. MVP.
- **US2 (T009–T011)**: after Foundational (needs the detail sub-table); frontend-only.
- **US3 (T012–T014)**: after Foundational; frontend-only; independent of US1/US2.
- **US4 (T015–T017)**: after US1 + US3 (verifies via their reads); frontend-only.
- **Polish (T018–T021)**: after the desired stories.

### Within each story
- The frontend manifest test ([P]) is written first (red), then the functions, then verify.

### Parallel Opportunities
- Setup: T002 after T001.
- Foundational: T004 (sub-table test) alongside T003 (sources); then T005.
- Each story's test task ([P]) precedes its function task.
- Polish: T018 ∥ T019 ∥ T020.

---

## Implementation Strategy

### MVP First (US1)
Sync main → Setup → Foundational → US1 → **STOP & VALIDATE**: browse customers, open one, see its tier +
entitlements — the third per-service UI, entirely frontend, reusing the 012/013 framework.

### Incremental Delivery
US1 (customers + tiers) → US2 (quote) → US3 (rules) → US4 (writes). **No backend code in any story** — every CRM
read/write already exists; the only framework touch is the shared detail sub-table (Foundational).

---

## Notes
- **0 backend changes** — all CRM reads/writes exist; the quote is a compute POST rendered verbatim (no
  client-side pricing math).
- **No new input types** — reuses the 012/013 `reference`/`list` inputs; the one framework addition is the shared
  **detail sub-table** for nested-array result fields.
- crm-service is role-gated; in the sim's stub mode the demo session has all roles (else a clear 403).
- Monetary/decimal values are displayed exactly as returned.
- Commit after each story (or logical group); simple messages, no AI attribution.
