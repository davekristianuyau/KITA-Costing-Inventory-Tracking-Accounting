# Implementation Plan: Procurement Service UI

**Branch**: `015-procurement-ui` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/015-procurement-ui/spec.md`

## Summary

Fill the 011 console's Procurement tab with the **full procurement-service manifest** — supplier master, the
purchase-order lifecycle (draft → approved → sent → received → closed), receiving (which the backend posts to
`operations-service`), and restock/reorder suggestions — each a manifest function rendered by the 011
`FunctionWorkspace` and called through the 009 edge, **reusing the 012/013/014 shared framework**. Phase 0 read
the procurement-service controllers/DTOs: **every read and write the user stories need already exists**, and the
PO/receipt/suggestion `lines[]` render via the **detail sub-table shipped in 014**. So — like 014 — **015 adds NO
backend code and NO new frontend framework**; it is purely the manifest.

## Technical Context

**Language/Version**: TypeScript 5.5 / React 18 / Vite 5 (evolve `frontend/`, on 011 + the 012/013/014 shared
framework). No backend code.
**Primary Dependencies**: the 011 design system + workspace framework, the shared inputs (`ReferenceInput`,
`ListInput`, `FieldInput`, `result/idLabels`, the 013 `bodyInput`/dotted-name bodies, the **014 detail
sub-table** for nested-array results), the generic edge fetch (`src/api/edge.ts`).
**Storage**: none client-side; session is the 009 httpOnly cookie.
**Testing**: **Vitest + Testing Library** — the Procurement manifest render/run (suppliers/POs/suggestions/writes);
reuses the 012/013/014 input + sub-table coverage.
**Target Platform**: modern browsers, responsive to 768px.
**Project Type**: Web app (frontend only).
**Performance Goals**: workspace interactions feel instant; pickers load once per function open.
**Constraints**: every UI call via the 009 edge with the client session; **0 backend changes**; receiving's
cross-service goods-receipt posting is done by the **backend**, not the UI (FR-012); monetary/decimal values
displayed exactly as returned; WCAG-AA + keyboard (011).
**Scale/Scope**: ~18 manifest functions across 4 areas (Suppliers, Purchase orders, Reorder, Receiving). No new
framework, no backend endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec present; this plan precedes code; P1–P3 stories independently testable. |
| II. Test-Driven Development | ✅ Red-first Vitest for the Procurement manifest render/run. No procurement logic added — PO totals, lifecycle transitions, and the cross-service goods receipt stay server-side; the UI only invokes + displays. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; **0 backend changes**; procurement-service is role-gated but stub mode (sim default) grants the demo session all roles; receiving posts to operations **via the backend**, never from the UI. |
| IV. Environment Isolation | ✅ Frontend-only; runs against the 011 local env; 0 real cloud. |
| V. Observability & Debuggability | ✅ Every function keeps 011's loading/empty/result/error states; lifecycle/over-receipt + cross-service failures surface clearly (FR-011, edge cases). |
| VI. Simplicity & YAGNI | ✅ No new framework — reuses 012/013/014 inputs + the detail sub-table. No backend code. Nothing to justify. |
| VII. Automated Quality Gates | ✅ Frontend build/test runs in CI (011 `frontend` job). No backend job change. |

**Result**: PASS — no complexity to track (the simplest per-service UI yet).

## Project Structure

### Documentation (this feature)

```text
specs/015-procurement-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   └── procurement-manifest.md   # the concrete Procurement manifest (function → method/path/inputs/result)
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                                 # EVOLVE — reuses the 011 + 012/013/014 shared framework (NO backend, NO new framework)
├── src/services/
│   ├── registry.ts                       # point the `procurement` entry at the manifest below
│   └── manifests/procurement.ts          # NEW: the full Procurement manifest (Suppliers / Purchase orders / Reorder / Receiving)
└── tests/ProcurementManifest.test.tsx    # NEW: Procurement manifest renders + runs each area (mock the edge)
```

**Structure Decision**: Evolve `frontend/` reusing the 011 + 012/013/014 shared framework. **No `backend/`
changes and no new framework** — every procurement read/write exists and the PO/receipt/suggestion `lines[]`
render via the 014 detail sub-table. ⚠️ **This branch predates the 012–014 merges** — before implementing, sync
`main` into `015-procurement-ui` so the shared inputs + the detail sub-table are present.

## Complexity Tracking

*No violations — nothing to track. (Frontend-only, no new framework, no backend code.)*

## Phase 0 — Research (see research.md)

Grounded by reading procurement-service's controllers/DTOs: the exact endpoint inventory (all needed reads +
writes exist → **no backend addition**); the **role-gating** model (stub mode → demo session gets all roles);
that **receiving** (`POST /purchase-orders/{id}/receipts`) posts the goods receipt to operations **in the
backend** (the UI only triggers it); and that the PO/receipt/suggestion `lines[]` render via the **014 detail
sub-table** — so **no new frontend framework** is needed.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced procurement entities (Supplier, Supplier Item, Supplier History, Purchase Order
  + lines, Goods Receipt + lines, Restock Suggestion + lines) with the fields their DTOs return.
- **contracts/**: the concrete Procurement **manifest** (every function → method + edge path + inputs + result),
  with `supplierId` reference pickers + id→label and PO/suggestion lines rendered via the detail sub-table.
- **quickstart.md**: bring up the 011 env → sign in → browse suppliers + POs → view suggestions → create a
  supplier + PO + approve → receive against a PO (stock reflects it in Operations) → `npm test` + `npm run build`.

**Post-design constitution re-check**: PASS — frontend-only, reuses 009 security + the full 011–014 framework, no
new code paths to justify.
