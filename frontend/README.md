# frontend/

KITA web frontend — **React 18 + TypeScript + Vite 5**, served in production by **Nginx** (static
assets + HTTP caching + `/auth` and `/api` reverse proxy to the edge gateway).

## The service console (011)

A polished console foundation on top of the 009 auth/edge:

- **Login** (`src/pages/Login.tsx`) — redesigned, branded sign-in (company/username/password) with a
  pre-auth **theme toggle**. Auth is the 009 httpOnly cookie session; errors stay generic.
- **Shell** (`src/app/`) — after sign-in, a top bar (brand, theme toggle, identity, sign-out) over
  **one tab per service** (`TopTabs`); the URL is the source of truth (`/app/:service`,
  `/app/:service/:function`).
- **Workspace framework** (`src/workspace/FunctionWorkspace.tsx`) — a service tab shows a left pane of
  its functions (`Sidebar`); selecting one renders a run-form from the function's `inputs`, runs it via
  the edge, and shows **loading / result (table·json·detail·message) / empty / error**. Driven entirely
  by a per-service **manifest** (`src/services/`) — the seam that follow-on per-service specs fill. Ships
  with **one reference function** (Operations → Items → `GET /api/operations/items`).
- **Theme** (`src/theme/`) — light/dark/system via CSS variables + `data-theme`, persisted to
  `localStorage`, with a no-flash inline init in `index.html` (set before first paint).

Design system: **Tailwind CSS** tokens (`src/index.css`, `tailwind.config.js`) + **Radix UI** primitives
(`src/ui/`) + **lucide-react** icons. Full per-service UIs are **separate specs** (start with Operations).

### Operations UI (feature 012)

The first full per-service UI: `src/services/manifests/operations.ts` declares the whole Operations surface
(catalog, inventory, BOM, production, sales, costing) as manifest functions. It exercises the shared
workspace-framework additions from 012 (in `src/workspace/inputs/` + `src/workspace/result/`):

- **`reference` input** (`ReferenceInput`) — a searchable picker whose options load once from a list endpoint
  (e.g. `GET /api/operations/items`), so users pick by SKU/name instead of typing a UUID (FR-017).
- **`list` input** (`ListInput`) — repeatable rows for array bodies (BOM components, order/receipt lines).
- **id→label resolution** (`result/idLabels.ts`) — a function's `resultRefs` relabel UUID result columns to a
  human label (e.g. item id → `SKU — name`).

These live in the shared framework so every later per-service UI (013–016) inherits them.

### HR & Payroll UI (feature 013)

`src/services/manifests/hr.ts` declares the whole HR surface (employees + compensation, attendance, leave,
payroll runs, payslip/register/remittance) — **reusing the 012 inputs**, no new framework. Two small, generic
additions to `FunctionWorkspace` support HR's request shapes (both used by later services too):

- **`bodyInput`** on a function — send that one input's value as the whole request body **unwrapped**, e.g. the
  DTR ingest which takes a raw `List<DtrRequest>` array.
- **dotted input names** (`period.startDate`) build a **nested** object body, e.g. the payroll-run `period`.

HR endpoints are role-gated; in the sim's stub security mode the demo session acts as `HR_ADMIN`. Statutory ids
are masked server-side; the UI displays results as-is and stores nothing sensitive.

### CRM UI (feature 014)

`src/services/manifests/crm.ts` declares the whole CRM surface (customers + entitlements, the cascading discount +
statutory rules, loyalty tiers, and the **price-quote preview**) — **frontend-only** (every crm-service read/write
already exists) and reusing the 012/013 inputs. The quote is `POST /discounts/compute` (customer + sale date +
**line items** list → an itemized result rendered verbatim). One small generic addition to `FunctionWorkspace`:
the **detail view renders an array-of-objects field as a nested sub-table** (used by the quote's `breakdown[]`).
CRM is role-gated; in stub mode the demo session has all roles.

### Dynamic edge calls

`/auth/*` uses the typed `openapi-fetch` client (`src/api/client.ts`, schema-checked). Manifest paths are
dynamic (not in the schema), so the workspace uses a **generic authenticated fetch**
(`src/api/edge.ts`, `credentials: "include"`).

## Commands

```bash
npm install
npm run dev      # Vite dev server (proxies /auth and /api to the edge, default http://localhost:8080)
npm test         # Vitest: Login, Console shell, Workspace framework
npm run build    # tsc --noEmit + vite build
```

The API client for `/auth` is generated from `../contracts/openapi.yaml` via `npm run gen:api`.

## Running the whole console locally

See [`sim/console/`](../sim/console/): `console-up.sh` brings up **floci-aws** (Docker socket → real
compute + the **Floci UI** on `:4500`), the client's imitated AWS deploy, and the 009 backend/edge/frontend.
Only the frontend and the Floci UI are host-exposed; **0 real cloud**.
