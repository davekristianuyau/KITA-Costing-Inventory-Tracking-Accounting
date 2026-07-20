# Implementation Plan: CRM Service UI

**Branch**: `014-crm-ui` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/014-crm-ui/spec.md`

## Summary

Fill the 011 console's Customers tab with the **full crm-service manifest** — customers, cascading discount +
statutory rules, loyalty tiers, entitlements, and the **price-quote preview** (`POST /discounts/compute`) — each
a manifest function rendered by the 011 `FunctionWorkspace` and called through the 009 edge, **reusing the
012/013 shared inputs** (reference picker, list input, id→label). Phase 0 read the crm-service controllers/DTOs:
**every read the user stories need already exists** (customers list/detail, entitlements, discount-rules,
discount-policy, loyalty-tiers; the quote is a compute POST). So — unlike 012/013 — **014 adds NO backend code**;
it is purely frontend, honoring the spec's original "no backend change".

The only framework touch is **one tiny generic result enhancement**: render an object result's array-valued
fields (e.g. the quote's `breakdown[]`) as a nested sub-table in the detail view, so the itemized quote reads
cleanly (SC-003). No new input types.

## Technical Context

**Language/Version**: TypeScript 5.5 / React 18 / Vite 5 (evolve `frontend/`, on 011 + the 012/013 shared
inputs). No backend code.
**Primary Dependencies**: the 011 design system + workspace framework, the **012/013 shared inputs**
(`src/workspace/inputs/ReferenceInput`, `ListInput`, `FieldInput`, `result/idLabels`, plus 013's `bodyInput` +
dotted-name nested bodies), the generic edge fetch (`src/api/edge.ts`).
**Storage**: none client-side; session is the 009 httpOnly cookie.
**Testing**: **Vitest + Testing Library** — the CRM manifest render/run (customers/quote/rules/writes) + the
nested-array detail sub-table; reuses the 012/013 input coverage.
**Target Platform**: modern browsers, responsive to 768px.
**Project Type**: Web app (frontend only).
**Performance Goals**: workspace interactions feel instant; pickers load once per function open.
**Constraints**: every UI call via the 009 edge with the client session; **0 backend changes**; the quote
**reflects the backend's computed result** (the UI never re-implements cascading/statutory/VAT math — FR-012);
monetary/decimal values displayed exactly as returned; WCAG-AA + keyboard (011).
**Scale/Scope**: ~14 manifest functions across 3 areas (Customers, Quote, Discount rules) + one small generic
result enhancement (detail sub-table). No backend endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec present; this plan precedes code; P1–P3 stories independently testable. |
| II. Test-Driven Development | ✅ Red-first Vitest for the CRM manifest render/run + the detail sub-table. No pricing math added — cascading/statutory/VAT stay server-side; the UI only displays the computed quote. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; **0 backend changes**; crm-service is role-gated but stub mode (sim default) grants the demo session all roles; no secrets in the frontend; the quote is the backend's result, not recomputed. |
| IV. Environment Isolation | ✅ Frontend-only; runs against the 011 local env; 0 real cloud. |
| V. Observability & Debuggability | ✅ Every function keeps 011's loading/empty/result/error states; role/validation errors surface clearly. |
| VI. Simplicity & YAGNI | ⚠️ One tiny generic result enhancement (array-valued detail fields → sub-table) for the quote breakdown. Justified below; no new input types, no backend code. |
| VII. Automated Quality Gates | ✅ Frontend build/test runs in CI (011 `frontend` job). No backend job change (no backend code). |

**Result**: PASS with one small tracked enhancement.

## Project Structure

### Documentation (this feature)

```text
specs/014-crm-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── crm-manifest.md                 # the concrete CRM manifest (function → method/path/inputs/result)
│   └── workspace-result-enhancement.md # the small generic detail sub-table addition
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                                 # EVOLVE — reuses the 011 + 012/013 shared framework (NO backend code)
├── src/
│   ├── services/
│   │   ├── registry.ts                   # point the `crm` entry at the manifest below
│   │   └── manifests/crm.ts              # NEW: the full CRM manifest (Customers / Quote / Discount rules)
│   └── workspace/FunctionWorkspace.tsx   # EXTEND (small): DetailView renders array-valued fields as a sub-table
└── tests/
    ├── CrmManifest.test.tsx              # NEW: CRM manifest renders + runs each area (mock the edge)
    └── (extend) Workspace.test.tsx       # the detail sub-table renders a nested breakdown array
```

**Structure Decision**: Evolve `frontend/` reusing the 011 + 012/013 shared inputs. **No `backend/` changes** —
every CRM read/write the stories need already exists. ⚠️ **This branch predates the 012/013 merges** — before
implementing, sync `main` into `014-crm-ui` so the shared inputs + `bodyInput`/dotted-name body building are present.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Detail sub-table for array-valued result fields | The quote (`ComputeDiscountResponse`) is an object with a `breakdown[]` array; a plain detail view would dump it as a JSON blob, failing SC-003's "every discount step, itemized". A tiny generic enhancement renders it as a table. | `result: "json"` shows the steps but not as a clean itemized breakdown; a CRM-only renderer would be less reusable than a small generic one. |

## Phase 0 — Research (see research.md)

Grounded by reading crm-service's controllers/DTOs: the exact endpoint inventory (all needed reads exist — **no
backend addition**); the **role-gating** model (stub mode → demo session gets all roles); and the reconciliations
the spec needs against reality — the quote takes **line items** (not a single "base amount") and returns an
itemized `breakdown[]` + `flags` (incl. VAT); a customer's **tiers** are composed from existing reads (customer
`type` + stored `loyaltyTierId` + entitlements + the quote's applied `tierCode`s); "assigning" a discount tier is
**rule-driven** (global discount-rules) while loyalty is set by the **evaluate** POST — there is no per-customer
"assign tier" endpoint, and none is added.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced CRM entities (Customer, Entitlement, Discount Rule, Discount Policy, Loyalty
  Tier, Quote/breakdown) with the fields their DTOs return.
- **contracts/**: the concrete CRM **manifest** (every function → method + edge path + inputs + result) and the
  **workspace-result-enhancement** contract (array-valued detail fields → sub-table).
- **quickstart.md**: bring up the 011 env → sign in → browse customers + tiers → run a quote → review rules →
  create/update a customer + entitlement → `npm test` + `npm run build`.

**Post-design constitution re-check**: PASS — frontend-only, reuses 009 security + the 011/012/013 framework, and
the single small result enhancement is tracked and generic.
