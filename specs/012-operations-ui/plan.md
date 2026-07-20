# Implementation Plan: Operations Service UI

**Branch**: `012-operations-ui` | **Date**: 2026-07-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/012-operations-ui/spec.md`

## Summary

Fill the 011 console's Operations tab with the **full operations-service manifest** — catalog, inventory, BOM,
production, sales, and costing — each capability a manifest function rendered by the 011 `FunctionWorkspace` and
called through the 009 edge. Two work streams:

1. **Frontend (bulk)**: the Operations manifest + a small, reusable **workspace-framework enhancement** (a
   searchable **record picker** so users choose items by SKU/name not UUID — FR-017 — plus enum selects,
   repeatable list inputs, and id→label result resolution).
2. **Backend (bounded, per clarification Q1=C)**: add the **read-only** endpoints the write-only resources lack
   (`GET /items/{id}`, list locations, list+get sales orders / builds / goods receipts — FR-015) so they are
   listable/viewable and every action is verifiable. **No existing endpoint or write/business logic changes.**

Phase 0 read the operations-service controllers to ground both streams; the reads are thin (existing
repositories are already tenant-scoped by the 008 schema-per-service model). See [research.md](./research.md).

## Technical Context

**Language/Version**: **Frontend** — TypeScript 5.5 / React 18 / Vite 5 / react-router 6 (evolve `frontend/`,
on the 011 foundation). **Backend** — Java 17 / Spring Boot 3.5 / Gradle (`backend/operations-service`, spec 003
conventions; see [[kita-backend-service-conventions]]).
**Primary Dependencies**: Frontend — the 011 design system (Tailwind + Radix + lucide), the 011 workspace
framework (`src/workspace/`), the service manifest (`src/services/`), and the generic authenticated edge fetch
(`src/api/edge.ts`). Backend — Spring Web + Spring Data JPA (existing repositories), reusing the existing
service/DTO layer.
**Storage**: Postgres, **schema-per-service** (008) — the new reads are `findAll`/`findById` on existing
repositories, already scoped to the client's schema (no new tables, no tenancy code).
**Testing**: Frontend — **Vitest + Testing Library** (manifest render/run, record picker, enum/list inputs,
id→label). Backend — **JUnit + Spring MockMvc contract tests** for each new GET (red-first per constitution);
Testcontainers ITs are CI-only (local Docker-daemon caveat, see [[hr-service-build-jdk-requirement]]).
**Target Platform**: modern browsers (responsive to 768px); the service runs as today.
**Project Type**: Web app — frontend + a bounded backend read addition.
**Performance Goals**: workspace interactions feel instant; a picker's list loads once per function open; new
reads are simple indexed queries.
**Constraints**: every UI call via the 009 edge with the client session; **backend change limited to read-only
endpoints** (FR-014/FR-015) — no change to existing endpoints or write/business logic; monetary/decimal values
displayed exactly as returned (never recomputed client-side); no secrets in the frontend; WCAG-AA + keyboard (011).
**Scale/Scope**: ~15 manifest functions across 6 areas + the record-picker/enum/list framework enhancement +
id→label resolution + **~7 new read endpoints** (item-by-id, locations list, sales-order list+get, build list+get,
receipt list+get) with contract tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec clarified (Q1/Q2 recorded); this plan precedes code; P1–P3 stories independently testable. |
| II. Test-Driven Development | ✅ Frontend: red-first Vitest for manifest render/run, record picker, enum/list inputs, id→label. Backend: red-first MockMvc contract tests for each new GET. No financial math added — costing/valuation stay server-side; the UI only displays. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; new endpoints are **read-only** and tenant-scoped by the 008 schema (no cross-client exposure); no writes added, no existing write/business logic touched; monetary values shown as received; no secrets in the frontend. |
| IV. Environment Isolation | ✅ Runs against the 011 local env (floci-aws + 009 edge); schema-per-service isolation preserved. |
| V. Observability & Debuggability | ✅ UI keeps 011's loading/empty/result/error states; new endpoints follow the service's existing logging/health. |
| VI. Simplicity & YAGNI | ⚠️ Two tracked items: (a) a **record-picker** input + enum/list inputs in the shared framework; (b) the **bounded backend read endpoints**. Both justified below. |
| VII. Automated Quality Gates | ✅ Frontend CI (011's `frontend` job: Vitest + build) + backend CI (`:operations-service:build` runs the new contract tests). |

**Result**: PASS with two tracked complexity items.

## Project Structure

### Documentation (this feature)

```text
specs/012-operations-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── operations-manifest.md              # the concrete Operations manifest (function → method/path/inputs/result)
│   ├── operations-read-api.md              # NEW: the backend read endpoints added (FR-015) + their response shapes
│   └── workspace-framework-extensions.md   # the shared 011 framework additions (record picker / enum / list / id-label)
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                                    # EVOLVE the 011 app
├── src/
│   ├── services/
│   │   ├── types.ts                         # EXTEND: InputField gains "reference" + "list" kinds
│   │   ├── registry.ts                      # operations entry → the full manifest below
│   │   └── manifests/operations.ts          # NEW: the full Operations manifest (all 6 areas + the new reads)
│   ├── workspace/
│   │   ├── FunctionWorkspace.tsx            # EXTEND: render reference/list inputs; id→label in result tables
│   │   ├── inputs/ReferenceInput.tsx        # NEW: searchable picker (load-once, client-side type-ahead — FR-017)
│   │   └── inputs/ListInput.tsx             # NEW: repeatable row group (BOM components, order/receipt lines)
│   └── api/edge.ts                          # REUSE (011): generic authenticated fetch
└── tests/
    ├── OperationsManifest.test.tsx          # manifest renders + runs each area (mock the edge)
    ├── ReferenceInput.test.tsx              # picker loads once, filters, validates required, submits the id
    └── (extend) Workspace.test.tsx          # list input + id→label rendering

backend/operations-service/                  # BOUNDED read-only addition (FR-015) — no existing behavior changes
├── src/main/java/com/kita/operations/
│   ├── api/CatalogController.java           # ADD: GET /items/{id}
│   ├── api/InventoryController.java         # ADD: GET /locations
│   ├── api/SalesOrderController.java        # ADD: GET /sales-orders, GET /sales-orders/{id}
│   ├── api/BuildController.java             # ADD: GET /builds, GET /builds/{id}
│   ├── api/GoodsReceiptController.java      # ADD: GET /receipts, GET /receipts/{id}
│   ├── {catalog,inventory,sales,production,procurement}/*Service.java  # ADD: list()/get(id) read methods
│   └── api/*Dtos.java                       # ADD: list/detail response shapes where the create-response is insufficient
└── src/test/java/com/kita/operations/       # ADD: MockMvc contract tests per new GET (red-first)
```

**Structure Decision**: Evolve `frontend/` on the 011 foundation (the bulk — manifest + the shared record-picker
framework enhancement) and make a **bounded, read-only** addition to `backend/operations-service` (the GET
endpoints from FR-015, built on existing repositories/DTOs with contract tests). No existing backend endpoint or
write/business logic is modified.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Backend read endpoints (FR-015) | Sales orders/builds/receipts are write-only; without reads, actions aren't verifiable and the UI can't list them. Clarification **Q1=C** chose to add them now to avoid future gaps. | Deferring (action-response-only) leaves the UI unable to list/re-open these records and makes acceptance untestable; the user explicitly rejected deferral. |
| Record-picker + list inputs (framework) | Id-taking reads and most writes take UUIDs; users can't type them. A searchable picker (FR-017) + repeatable list rows make forms usable. | Raw UUID text inputs are unusable; these live in the shared framework so every later per-service UI inherits them. |

## Phase 0 — Research (see research.md)

Grounded by reading operations-service's controllers/services/repositories: the exact endpoint inventory; the
**bounded read endpoints to add** (FR-015) and that they are thin (`findAll`/`findById` on existing repositories,
already schema-scoped, reusing existing DTOs where possible); the frontend input model (record picker + enum +
list); and the corrections carried from grounding (valuation = AVCO|FIFO; BOM explosion is a flat list;
item detail via `GET /items/{id}`).

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: the surfaced operations entities (with the now-listable SalesOrder/Build/GoodsReceipt +
  single Item + Location) and the manifest-model additions (reference/list `InputField` kinds).
- **contracts/**: the concrete Operations **manifest** (every function → method + edge path + inputs + result),
  the **operations-read-api** contract (the new GET endpoints + response shapes + read-only/tenant-scoped rules),
  and the **workspace-framework-extensions** contract (record picker / enum / list / id→label).
- **quickstart.md**: `:operations-service:build` (new reads green) → bring up the 011 env → exercise each area,
  including listing sales orders/builds/receipts → `npm test` + `npm run build`.

**Post-design constitution re-check**: PASS — the backend addition stays read-only and tenant-scoped with
contract tests; the frontend reuses 009 security + the 011 framework; both complexity items are tracked and
justified.
