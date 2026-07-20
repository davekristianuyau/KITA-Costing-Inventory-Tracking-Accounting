# Implementation Plan: Operations Service UI

**Branch**: `012-operations-ui` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-operations-ui/spec.md`

## Summary

Fill the 011 console's Operations tab with the **full operations-service manifest** — catalog, inventory, BOM,
production, sales, and costing — so each capability is a manifest function rendered by the 011
`FunctionWorkspace` and called through the 009 edge. The only frontend framework work is a small, reusable
**input enhancement**: a **reference picker** (a select whose options come from a list endpoint, so users pick an
item by SKU/name instead of typing a UUID) plus enum-backed selects, and optional **id→label resolution** in
result tables. **No backend changes** — operations-service (spec 003) already exposes every endpoint; Phase 0
inventoried them and reconciled the spec against what actually exists (only five GET reads exist; several
resources are write-only). See [research.md](./research.md).

## Technical Context

**Language/Version**: TypeScript 5.5 / **React 18 / Vite 5 / react-router 6** (evolve the existing `frontend/`,
building on the 011 console foundation). No backend code.
**Primary Dependencies**: the 011 design system (**Tailwind + Radix + lucide-react**), the 011 **workspace
framework** (`src/workspace/FunctionWorkspace.tsx`), the **service manifest** (`src/services/`), and the
**generic authenticated edge fetch** (`src/api/edge.ts`, `credentials:"include"`). `/auth` still uses the typed
openapi-fetch client; `/api/operations` uses the generic fetch (dynamic manifest paths).
**Storage**: none client-side; session is the 009 httpOnly cookie.
**Testing**: **Vitest + Testing Library**, extending 011's suite — the Operations manifest renders/runs, the new
reference-picker input, enum selects, id→label resolution, and each area's result rendering.
**Target Platform**: modern browsers, responsive to 768px (011 floor).
**Project Type**: Web app (frontend only).
**Performance Goals**: workspace interactions feel instant; a picker's option list loads once per function open;
reuse 011's <2s first-view budget.
**Constraints**: **0** backend/API changes; every call via the 009 edge with the client session; WCAG-AA + full
keyboard nav (011); no secrets/credentials in the browser.
**Scale/Scope**: ~15 manifest functions across 6 areas + one framework input enhancement (reference picker + enum
select) + optional id→label result resolution + tests. Full manifests for other services are their own specs.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec written; this plan precedes code; user stories P1–P3 independently testable. |
| II. Test-Driven Development | ✅ Red-first Vitest for the manifest render/run, the reference picker, enum selects, id→label, and per-area result rendering (list/detail/table). No financial math added client-side — costing/valuation stay in the backend; the UI only displays. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; **read-first**, and all writes go to existing validated endpoints; monetary values are displayed as received (decimals), never recomputed in the browser; no secrets in the frontend. No new trust boundary. |
| IV. Environment Isolation | ✅ Frontend-only; runs against the 011 local env (floci-aws + 009 edge); no environment coupling. |
| V. Observability & Debuggability | ✅ Every function keeps 011's explicit loading/empty/result/error states; errors surface the failing action's context. |
| VI. Simplicity & YAGNI | ⚠️ Adds a **reference-picker input** + enum selects + optional id→label resolution to the 011 framework. Justified: UUID-only text inputs are unusable, and every per-service UI needs the same picker — so it belongs in the shared framework, not this feature. Kept minimal (one new input kind + a small resolver). See Complexity Tracking. |
| VII. Automated Quality Gates | ✅ Extends 011's `frontend` CI job (Vitest + build); no new heavy jobs. |

**Result**: PASS with one tracked complexity item.

## Project Structure

### Documentation (this feature)

```text
specs/012-operations-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── operations-manifest.md          # the concrete Operations manifest (function → method/path/inputs/result)
│   └── workspace-framework-extensions.md  # the 011 framework additions this feature needs (picker/enum/id-label)
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                                   # EVOLVE the 011 app (no backend changes)
├── src/
│   ├── services/
│   │   ├── types.ts                        # EXTEND: InputField gains a "reference" kind (options from a list) + enum selects
│   │   ├── registry.ts                     # operations entry now points at the full manifest below
│   │   └── manifests/operations.ts         # NEW: the full Operations manifest (all functions across the 6 areas)
│   ├── workspace/
│   │   ├── FunctionWorkspace.tsx           # EXTEND: render the reference-picker input; resolve enum selects
│   │   ├── inputs/ReferenceInput.tsx       # NEW: a select whose options load from a list endpoint (via the edge)
│   │   └── result/                         # NEW (small): id→label resolution helper for result tables (item id → SKU)
│   └── api/edge.ts                         # REUSE (011): generic authenticated fetch
└── tests/
    ├── OperationsManifest.test.tsx         # the manifest renders + runs each area (mock the edge)
    ├── ReferenceInput.test.tsx             # picker loads options from a list endpoint; required validation
    └── (extend) Workspace.test.tsx         # enum select + id→label rendering
```

**Structure Decision**: Evolve `frontend/` on top of 011. The feature is **mostly data** (the Operations
manifest) plus a **small, reusable framework enhancement** (the reference-picker input + enum selects, which live
in the shared workspace framework so every later per-service UI inherits them). Operations-specific result
polish (item-id → SKU/name resolution) is a thin helper. No backend module is touched.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Reference-picker input (select options from a list endpoint) | The id-taking reads (availability, movements, cost) and most writes take item/location UUIDs; users cannot type UUIDs. A picker populated from `GET /items` (etc.) makes them usable. | Raw UUID text inputs are unusable in practice; hardcoding ids is wrong per-client. It lives in the shared framework because every per-service UI needs it. |
| id→label resolution in result tables | Read results carry UUIDs (itemId, locationId); showing SKU/name makes them legible. | Showing raw UUIDs makes every table unreadable; a one-time join against the items list is cheap and reusable. |

## Phase 0 — Research (see research.md)

Resolves the pivotal unknown by **reading operations-service's controllers**: the exact endpoint inventory
(five GET reads; the rest are POST writes; several resources are write-only) and the reconciliation of the spec
against it — item "detail" = the `GET /items` row; "reservations" = the `reserved`/`available` columns of
`GET /items/{id}/availability`; created sales orders/builds/receipts are verified by their action response (no
GET to re-list them). Also fixes: valuation methods are **AVCO|FIFO** (FEFO is a perishable/lot policy, not a
`ValuationMethod`); BOM explosion returns a **flat** requirements list (no recursive tree view needed); and the
input model (reference picker + enum selects) the framework needs.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced operations entities (Item, UoM, Location, Availability/StockLevel, Movement,
  BOM/ComponentRequirement, Build, SalesOrder/Line, GoodsReceipt, CostMargin) with the fields their DTOs return,
  plus the manifest-model additions (the `reference` InputField kind).
- **contracts/**: the concrete Operations **manifest** (every function → method + edge path + inputs + result
  shape, grounded in real endpoints) and the **workspace-framework-extensions** contract (reference picker, enum
  select, id→label resolution) the 011 framework must satisfy.
- **quickstart.md**: bring up the 011 local env → sign in → exercise each Operations area (list items, pick an
  item and view stock/movements/cost, explode a BOM, then create/build/post/sell) → `npm test` + `npm run build`.

**Post-design constitution re-check**: PASS — no new violations; reuses 009 security + the 011 framework, adds
only the justified shared input enhancement, and touches no backend code.
