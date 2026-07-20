# Implementation Plan: Service Console — Polished Frontend Foundation

**Branch**: `011-service-console-ui` | **Date**: 2026-07-19 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/011-service-console-ui/spec.md`

## Summary

Evolve the feature-009 frontend into a **polished console foundation**: a redesigned **login page**, a
**one-tab-per-service** navigation shell, a **light/dark theme** system, and the **per-service workspace
framework** (service tab → left pane of functions → workspace, driven by a per-service manifest, proven with one
reference function). It runs locally against the 009 backend/edge, with **`floci-aws` brought up with the Docker
socket** so it **runs real compute and the Floci UI (:4500) is accessible** — a finding **verified empirically**
in Phase 0 (an ECS task served real nginx over HTTP 200), correcting the earlier "Floci mocks compute" assumption.
Each service's **full UI is a follow-on per-service spec** (starting with Operations). See [research.md](./research.md).

## Technical Context

**Language/Version**: TypeScript 5.5 / **React 18 / Vite 5 / react-router 6** (evolve the existing
`frontend/`); Bash + Docker Compose (local env). No new backend code.
**Primary Dependencies**: **Tailwind CSS** (theming), **Radix UI** primitives (accessible tabs/menus/dialogs),
**lucide-react** (icons), **openapi-fetch** (existing) for edge calls; **Floci** (`floci/floci:4566` +
`floci/floci-ui:4500`, Docker socket mounted) + **floci-cli** for the local AWS environment; features 008/009
(backend, identity, edge) + 010 (`floci-aws` deploy).
**Storage**: none server-side; theme preference in browser **localStorage**; session is the 009 httpOnly cookie.
**Testing**: **Vitest + Testing Library** (login, shell, theme, nav, workspace framework, reference function),
extending 009's suite; a local end-to-end smoke (env up → login → run reference function → Floci UI reachable).
**Target Platform**: modern browsers (responsive to 768px); local Docker for the environment.
**Project Type**: Web app (frontend) + local environment wiring.
**Performance Goals**: theme switch <200 ms no-flash (SC-002); first console view <2 s (SC-006).
**Constraints**: **0** real cloud credentials/spend (SC-007); only the frontend + Floci UI host-exposed;
WCAG AA contrast both themes + full keyboard nav (SC-005).
**Scale/Scope**: 1 login redesign + console shell + theme + workspace framework + 1 reference function +
env wiring. Full per-service UIs are **out of scope** (their own specs).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Spec-Driven Development | ✅ spec + clarification complete; plan precedes code. |
| II. Test-Driven Development | ✅ Red-first Vitest for login/theme/nav/workspace + the reference function; a11y assertions. |
| III. Security & Data Integrity | ✅ Reuses 009's httpOnly session + edge tenancy; no secrets in the frontend; **0 real cloud** (floci-aws local, dummy creds). No new trust boundaries. |
| IV. Environment Isolation | ✅ Local-only; floci-aws is a local emulator; per-client isolation preserved by the edge (009). |
| V. Observability & Debuggability | ✅ Clear loading/empty/error/unauthorized states (FR-010); the Floci UI gives deployment visibility. |
| VI. Simplicity & YAGNI | ⚠️ Adds a design system (Tailwind/Radix/lucide) + a workspace framework + Docker-socket floci wiring. Justified: "beautiful/modern + accessible" is the ask, and the per-service split keeps 011 small. Kept minimal — evolve 009, one reference function, framework-not-full-UI. See Complexity Tracking. |
| VII. Automated Quality Gates | ✅ Frontend build/test runs in CI (extends 009's `frontend` job); env smoke can be a non-blocking job. |

**Result**: PASS with one tracked complexity item.

## Project Structure

### Documentation (this feature)

```text
specs/011-service-console-ui/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/
│   ├── navigation.md        # routes + one-tab-per-service + left-pane/workspace model
│   ├── theme.md             # light/dark contract (persist, no-flash, a11y)
│   ├── service-manifest.md  # the per-service function manifest schema (seam to per-service specs)
│   └── local-environment.md # floci-aws (docker socket) + Floci UI + edge + only-frontend-exposed
└── checklists/requirements.md
```

### Source Code (repository root)

```text
frontend/                         # EVOLVE the 009 app
├── src/
│   ├── pages/Login.tsx           # redesigned (Tailwind/Radix/lucide)
│   ├── app/                      # NEW console shell: AppLayout, TopTabs (one per service), Sidebar (functions)
│   ├── theme/                    # NEW ThemeProvider + toggle + no-flash init + CSS variables
│   ├── services/                 # NEW service registry + per-service function manifests (+ reference fn)
│   ├── workspace/                # NEW workspace framework: FunctionWorkspace (run-form + result/loading/error)
│   ├── auth/ (009), api/ (009)   # reuse: session context + openapi-fetch edge client
│   └── routes/                   # /login, /app/:service, /app/:service/:function
├── tailwind.config.* , src/index.css   # design tokens + theme variables
└── tests/                        # Vitest: login, theme, tabs, sidebar, workspace, reference function

sim/                              # local environment
└── (extend) floci-aws brought up WITH the Docker socket + `-u root` + floci-ui (:4500); deploy via 010;
    run 009 backend/edge/frontend; only frontend + Floci UI host-exposed
```

**Structure Decision**: Evolve `frontend/` (not a rewrite) with a design system + a service-agnostic console
shell and workspace **framework**; a per-service **manifest** is the seam that follow-on per-service specs fill.
The local environment brings up `floci-aws` correctly (Docker socket → real compute + Floci UI) and reuses the
009 backend/edge for service calls.

## Complexity Tracking

| Violation (Principle VI) | Why needed | Simpler alternative rejected because |
|---|---|---|
| Design system (Tailwind + Radix + lucide) | "Beautiful/modern + accessible (WCAG AA, keyboard-nav)" is the explicit ask; Radix supplies correct tab/menu a11y | Hand-rolled components risk broken a11y and inconsistent visuals; a heavier UI kit imposes a canned look |
| Workspace framework + per-service manifest | Enables the per-service-spec split — one reusable frame many specs fill | Building each service's UI inline in 011 would make it an ERP-sized feature (the split's whole point) |
| floci-aws with the Docker socket + Floci UI | The user requires a running floci-aws + accessible UI; verified it runs real compute | Mounting nothing leaves the UI broken and compute inert (the exact reported error) |

## Phase 0 — Research (see research.md)

Resolves — with an **empirical spike** — the pivotal unknown: **Floci runs real compute** (ECS task served nginx
HTTP 200) and the **Floci UI is `floci/floci-ui:4500`, needing the Docker socket** (the reported error's fix).
Also fixes the frontend stack (evolve 009), the design system (Tailwind/Radix/lucide), theming (no-flash CSS
vars + localStorage), the one-tab-per-service URL-driven nav, service calls via the 009 edge, and the per-service
**function manifest** seam.

## Phase 1 — Design & Contracts (see data-model.md, contracts/, quickstart.md)

- **data-model.md**: Session/Client (009), Backend Service (tab), Service Function (manifest entry), Theme
  Preference, floci-aws Deployment.
- **contracts/**: navigation model, theme contract, the service-function-manifest schema (the split seam), and
  the local-environment contract (floci-aws socket + Floci UI + edge + host exposure).
- **quickstart.md**: bring the env up (floci-aws w/ socket + UI, backend, console) → log in → run the reference
  function → confirm the Floci UI opens — all with 0 real cloud.

**Post-design constitution re-check**: PASS — no new violations; the design reuses 009's security model and keeps
the foundation minimal via the per-service split.
