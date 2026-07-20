---
description: "Task list for 011-service-console-ui"
---

# Tasks: Service Console — Polished Frontend Foundation

**Input**: Design documents from `/specs/011-service-console-ui/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — the constitution mandates TDD; Vitest + Testing Library specs are written first per story.

**Organization**: By user story. **Evolves the existing `frontend/`** (feature 009) — do not rewrite. Design
system = Tailwind + Radix primitives + lucide-react. Full per-service UIs are **out of scope** (their own specs);
011 ships the foundation + framework + **one reference function**.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on incomplete tasks.

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add design-system deps to `frontend/package.json` (`tailwindcss` + `postcss` + `autoprefixer`, `@radix-ui/react-tabs` + `@radix-ui/react-dropdown-menu` + `@radix-ui/react-dialog`, `lucide-react`); `npm install`; create `frontend/tailwind.config.js` + `frontend/postcss.config.js`; wire Tailwind into `frontend/src/index.css`
- [X] T002 [P] Define the design tokens + theme CSS variables in `frontend/src/index.css` (color/spacing/radius tokens; light + dark palettes via `:root` and `[data-theme="dark"]`, WCAG-AA contrast) and Tailwind theme mapping in `frontend/tailwind.config.js`

---

## Phase 2: Foundational (Blocking Prerequisites for US1–US4)

**⚠️ Blocks all user stories.**

- [X] T003 Theme system in `frontend/src/theme/` — `ThemeProvider.tsx` (light/dark/system, `data-theme` on root, persist to localStorage) + `ThemeToggle.tsx` (lucide sun/moon) + a **no-flash inline init** snippet added to `frontend/index.html` that sets `data-theme` before first paint (contracts/theme.md)
- [X] T004 [P] Shared accessible UI primitives in `frontend/src/ui/` — `Tabs.tsx` (wrap `@radix-ui/react-tabs`), `Menu.tsx` (dropdown), `Dialog.tsx`, `Button.tsx`, `Card.tsx`, `Field.tsx`, `Icon.tsx` (lucide helper), all styled with the design tokens
- [X] T005 [P] Service registry + manifest types in `frontend/src/services/` — `types.ts` (`ServiceManifest`/`ServiceFunction`/`InputField`, contracts/service-manifest.md) + `registry.ts` (the client's services: id/label/icon/basePath for operations/hr/crm/procurement/workflow, each with a **reference function** entry)

**Checkpoint**: theme works app-wide (no flash), primitives + the manifest types/registry exist.

---

## Phase 3: User Story 1 - Beautiful, modern sign-in (Priority: P1) 🎯 MVP

**Goal**: A redesigned, modern login page (current icons, clean layout) with the 009 auth + a theme toggle
available pre-login.

**Independent Test**: Open the app → modern login renders (company/username/password + submit + theme toggle);
toggling switches light/dark instantly; valid sign-in proceeds, invalid → clear generic error.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T006 [P] [US1] Rewrite `frontend/tests/Login.test.tsx` for the redesign: renders the modern layout + the 3 fields + submit + a **theme toggle**; toggling sets `data-theme`; valid credentials → redirect into the app; invalid → generic non-revealing error; no token in JS-readable storage (reuse 009 mock of `api`)

### Implementation for User Story 1

- [X] T007 [US1] Redesign `frontend/src/pages/Login.tsx` with Tailwind/Radix/lucide (branded, uncluttered, current icons; company/username/password; primary sign-in; idle→submitting→error states) — **preserve** 009 auth (`useAuth().login`, generic errors, cookie session)
- [X] T008 [US1] Place the `ThemeToggle` on the login page (works pre-auth) and confirm the no-flash init applies on first paint
- [X] T009 [US1] Verify US1: `cd frontend && npm test` (Login) + `npm run build` green

**Checkpoint**: MVP — a polished, themeable login that authenticates via the 009 edge.

---

## Phase 4: User Story 2 - Console shell: one tab per service (Priority: P2)

**Goal**: After sign-in, a console with **one top tab per service**; selecting a tab opens that service's
workspace; theme + identity + sign-out in the shell.

**Independent Test**: Sign in → land in the console; top tabs = the client's services; selecting a tab navigates;
theme toggle + user/client visible; sign-out → `/login`.

### Tests for User Story 2 ⚠️ (write first, must fail)

- [X] T010 [P] [US2] `frontend/tests/Console.test.tsx`: after auth, `TopTabs` renders one tab per registry service; selecting a tab updates the route to `/app/:service`; theme toggle + user/client + sign-out present; unauthenticated → redirected to `/login`

### Implementation for User Story 2

- [X] T011 [US2] `frontend/src/app/AppLayout.tsx` — the shell: top bar (brand, `ThemeToggle`, user/client, sign-out) + `TopTabs` (one per service from `registry`) + a content `<Outlet/>`
- [X] T012 [US2] `frontend/src/app/TopTabs.tsx` — one tab per service (Radix Tabs, accessible, active tab derived from the URL), navigating to `/app/:service`
- [X] T013 [US2] Routing in `frontend/src/App.tsx` + `frontend/src/routes/` — `/app` → redirect to the first service; `/app/:service` and `/app/:service/:function` under the protected `AppLayout` (reuse 009 `ProtectedRoute`); replace the 009 placeholder `Dashboard`
- [X] T014 [US2] Verify US2: tests + build green; tabs are keyboard-navigable (arrow keys, focus visible)

**Checkpoint**: the navigation shell works — one tab per service, theme, sign-out.

---

## Phase 5: User Story 3 - Per-service workspace framework + reference function (Priority: P2)

**Goal**: A service tab shows a **left pane of its functions** + a **workspace**; the framework renders any
manifest and runs a function via the edge; proven with **one reference function**.

**Independent Test**: Open a service → left pane lists its functions; select the reference function → workspace
opens; run it → loading → real result (or clear error); switching functions updates without a full reload.

### Tests for User Story 3 ⚠️ (write first, must fail)

- [X] T015 [P] [US3] `frontend/tests/Workspace.test.tsx`: `Sidebar` lists a service's functions; selecting one routes to `/app/:service/:function`; `FunctionWorkspace` shows the run-form, a loading state on run, then a result/error (mock the edge `api`); switching functions swaps the workspace without a reload

### Implementation for User Story 3

- [X] T016 [US3] `frontend/src/app/Sidebar.tsx` — left pane rendering the selected service's `functions` (from its manifest); selecting one navigates to `/app/:service/:function`; accessible list + active state
- [X] T017 [US3] `frontend/src/workspace/FunctionWorkspace.tsx` — render a `ServiceFunction`: a run-form from `inputs` (required-field validation) + Run; on run, call `method` `basePath+path` (substituting `{params}`) through the **009 edge** via `frontend/src/api/` (openapi-fetch, cookie); render **loading / result (table|json|detail|message) / empty / error** states
- [X] T018 [US3] Add a real **reference function** to one service's manifest in `frontend/src/services/registry.ts` (a safe read/health call, e.g. operations) wired end-to-end through the edge
- [X] T019 [US3] Wire `/app/:service/:function` to render `Sidebar` + `FunctionWorkspace` for the selected function (in `frontend/src/app/`)
- [X] T020 [US3] Verify US3: tests (workspace + reference function) + build green

**Checkpoint**: the reusable per-service frame works end-to-end; per-service specs can now fill manifests.

---

## Phase 6: User Story 4 - Local floci-aws environment + Floci UI (Priority: P3)

**Goal**: One documented startup brings up `floci-aws` (Docker socket → real compute + **Floci UI :4500**), the
client's AWS resources deployed, the 009 backend, and the console — browser-accessible, 0 real cloud.

**Independent Test**: Run the startup → floci-aws + Floci UI up, resources deployed, console reachable; log in +
run the reference function; the Floci UI opens with no "container runtime" error.

- [ ] T021 [US4] `sim/console-up.sh` (+ any compose) — bring up **floci-aws with `-v /var/run/docker.sock:/var/run/docker.sock` and `-u root`** and the **Floci UI (`floci/floci-ui:4500`)**, then the 009 backend/edge/frontend, and deploy the client's AWS resources (reuse feature 010); **only the frontend + Floci UI host-exposed** (contracts/local-environment.md)
- [ ] T022 [US4] `sim/console-down.sh` + confirm the **Floci UI opens** (`curl -sf localhost:4500` → 200, no *"could not reach the container runtime"* error) and that datastores/edge/emulator internals are not host-exposed
- [ ] T023 [US4] `sim/console-smoke.sh` — env up → console reachable → login as a demo client → run the reference function (200/routed) → Floci UI reachable → **0 real cloud creds**; independent teardown

**Checkpoint**: the whole console runs locally against a live, inspectable floci-aws.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T024 [P] Accessibility pass: WCAG-AA contrast in **both** themes, full keyboard nav (tabs, sidebar, forms), visible focus, and responsive layout down to **768px** (SC-005) across `frontend/src/`
- [ ] T025 [P] Docs: update `frontend/README.md` + a note in root `README.md` describing the console (login → one-tab-per-service → workspace) and the local floci-aws startup
- [ ] T026 [P] CI: extend the `frontend` job in `.github/workflows/ci.yml` to run the new Vitest suites + build; optionally a **non-blocking** `console-smoke` job running `sim/console-smoke.sh`
- [ ] T027 [P] Perf: verify theme switch **<200 ms no-flash** (SC-002) and first console view **<2 s** locally (SC-006)
- [ ] T028 Full verification: `cd frontend && npm test` + `npm run build` green; run `quickstart.md` end-to-end (login → console → reference function → Floci UI)

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003–T005)** → user stories.
- **US1 (T006–T009)**: after Foundational (theme + primitives). MVP.
- **US2 (T010–T014)**: after Foundational + US1 (need to be signed in); uses the registry + primitives.
- **US3 (T015–T020)**: after US2 (the shell) + the manifest framework (T005).
- **US4 (T021–T023)**: after US1–US3 + feature 010 (deploy) + feature 009 (backend). Reuses this session's
  verified Floci-with-socket finding.
- **Polish (T024–T028)**: after the desired stories.

### Within each story
- The Vitest spec ([P]) is written first (red), then the components, then verify.

### Parallel Opportunities
- Setup: T002 with T001's install.
- Foundational: T004/T005 in parallel (different dirs).
- Each story's test task ([P]) alongside its first component.
- Polish: T024/T025/T026/T027 in parallel.

---

## Implementation Strategy

### MVP First (US1)
Setup → Foundational → US1 → **STOP & VALIDATE**: a beautiful, themeable login that authenticates via the edge.

### Incremental Delivery
US1 (login) → US2 (shell: one tab per service) → US3 (workspace framework + reference function) → US4 (local
floci-aws env + UI). Each is independently testable. **Full per-service UIs are follow-on specs** (start with
Operations), built on the US3 framework via the per-service manifest.

---

## Notes
- **Evolve `frontend/`**, don't rewrite — reuse 009's `auth/`, `api/`, `ProtectedRoute`, and Vitest setup.
- **0 real cloud** — floci-aws is local with dummy creds; only the frontend + Floci UI are host-exposed.
- The **service manifest** is the seam to the per-service specs — keep 011 to the framework + one reference function.
- Commit after each task or logical group; simple messages, no AI attribution.
