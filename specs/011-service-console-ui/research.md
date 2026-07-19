# Phase 0 Research — Service Console (Polished Frontend Foundation)

## D0. Does floci-aws actually run/serve compute? — **YES (verified empirically)**

- **Decision / finding**: With the host **Docker socket mounted** (`-v /var/run/docker.sock:/var/run/docker.sock`,
  run as **root**), Floci-AWS **runs real containers** for compute resources. Live test: created an ECS cluster +
  task def (`nginx:alpine`) + `run-task` → Floci spawned a real container `floci-ecs-…-web` (Docker Hub pull),
  published `8099->80`, and `curl http://localhost:8099/` returned **HTTP 200 "Welcome to nginx!"**. Floci's
  service list also includes lambda/ecs/ecr/eks/elbv2/ec2/batch (68+ services).
- **Rationale**: This overturns the earlier **untested** "Floci only mocks compute" assumption (spec 010 note).
  It means the client's deployment on `floci-aws` can genuinely **run and serve** — the browser can reach the
  deployed app, not just an inert control-plane mock.
- **Implication for scope**: Full app-hosting-on-floci-aws (deploy identity/edge/services as ECS tasks with the
  real kita images) is now *possible*, but it is a **larger environment effort** (kita images must be reachable
  by Floci's ECS — pushed to Floci ECR or a local registry, as nginx pulled from Docker Hub). **011 (foundation)
  reaches services through the proven 009 edge; hosting the whole app on floci-aws ECS is an enabled follow-on.**
- **Alternatives considered**: assume mocked compute (rejected — empirically false); require real cloud (rejected
  — out of scope).

## D1. Floci UI — `floci/floci-ui`, port 4500, needs the socket

- **Decision**: The Floci web console is a **separate image `floci/floci-ui:latest` on port `4500`**; it
  **auto-starts when the Docker socket is available** and is what the user's *"Could not start the Floci UI:
  could not reach the container runtime"* error refers to (the socket wasn't mounted). Verified: with the socket
  mounted, `floci-ui` came up and `curl http://localhost:4500/` → **HTTP 200**.
- **Rationale**: Fixes the reported error and gives the operator a deployment-inspection console (FR-007/US4).
- **Bring-up**: `floci-cli` (`floci start`) mounts the socket by default and manages the emulator + UI; the sim
  can equivalently run the emulator image with the socket + `-u root` and expose `4500`.

## D2. Frontend foundation — evolve the existing 009 app (do not rewrite)

- **Decision**: Build on the existing **React 18 / Vite 5 / react-router 6 / openapi-fetch** frontend (feature
  009). 011 **redesigns** the login page and **adds** the console shell, theme system, and workspace framework —
  it does not start a new app.
- **Rationale**: 009's auth/session/edge wiring and build/test tooling already work; reuse them (Constitution VI).
- **Alternatives**: a fresh SPA (rejected — throws away working auth/session/CI).

## D3. Design system + icons

- **Decision**: **Tailwind CSS** (utility styling + theming via CSS variables) + **Radix UI primitives**
  (accessible tabs, dialog, dropdown, etc.) + **lucide-react** icons — all modern, open (MIT), actively
  maintained, and accessibility-first.
- **Rationale**: Meets "latest icons/UI/UX" + the FR-009/SC-005 accessibility bar (keyboard-nav, contrast)
  without hand-rolling a11y; Radix gives correct tab/menu semantics; lucide is a large current icon set.
- **Alternatives**: MUI/Chakra (heavier, opinionated look); hand-rolled components (a11y risk); Font Awesome
  (licensing/older feel). Rejected.

## D4. Theme (light/dark), instant + no-flash + persisted

- **Decision**: Theme via a `data-theme` attribute (or `.dark` class) on the document root driving **CSS
  variables**; default follows **`prefers-color-scheme`**; user choice persisted in **localStorage**; a tiny
  **inline pre-hydration script** sets the initial theme before first paint to avoid a flash (SC-002:
  <200 ms, no flash).
- **Rationale**: Standard, framework-light, flicker-free.

## D5. Navigation model — one tab per service → left pane of functions → workspace

- **Decision**: Routes: `/login`, `/app` (redirects to the first service), `/app/:service`, `/app/:service/:function`.
  **Top tabs = the client's services** (the tabs *are* the service selection). The selected service renders a
  **left pane of its functions** + a **workspace** for the selected function. State in the URL so views are
  linkable and back/forward works.
- **Rationale**: Matches the clarified "one tab per service" + "left pane for functions"; URL-driven keeps it
  simple and testable.

## D6. How the console reaches services — via the 009 edge (proven)

- **Decision**: The console calls backend services through the existing **009 edge** (`/api/**`, httpOnly session
  cookie, tenant-routed) — the reliable, already-built path. `floci-aws` is the **deployment target** (resources
  provisioned via 010) and is **inspectable via the Floci UI**. (Per D0, routing app traffic *through* floci-aws
  ECS is possible but is the enabled follow-on, not the foundation.)
- **Rationale**: The console UX (browser → console → services) is identical either way; going through the edge
  avoids coupling the foundation to a full app-on-floci-aws re-host. Keeps 011 shippable.

## D7. Service function metadata — a per-service manifest

- **Decision**: Each service's left-pane functions come from a small **per-service function manifest** (function
  id, label, icon, HTTP method + path template, input fields, result shape). 011 ships the **framework** that
  renders a manifest into a left pane + a generic run-form/result workspace, plus **one reference function**
  (e.g. a service health/read call) proving it end-to-end. **Each per-service spec authors that service's full
  manifest + any bespoke workspaces.**
- **Rationale**: Lets the foundation be service-agnostic while the per-service specs add real depth; the manifest
  is the seam between 011 and the follow-on specs.
- **Alternatives**: generate from each service's OpenAPI (possible later; heavier for the foundation); hardcode
  per service (defeats the framework). 

## D8. Local environment / sim wiring

- **Decision**: Extend the local run so `floci-aws` comes up **with the Docker socket mounted + `-u root`** (so
  compute runs and the **Floci UI (:4500)** is accessible), the client's AWS resources are deployed (010), the
  009 backend + edge + frontend run, and **only the frontend + the Floci UI are host-exposed**. Prefer
  **floci-cli** for lifecycle where convenient; otherwise a compose service with the socket mount.
- **Rationale**: One documented startup gives the browser-accessible console + a live, inspectable floci-aws.

## Resolved unknowns

- "Does floci-aws serve the app in the browser?" → **Yes, Floci runs real ECS containers (verified)**; the
  foundation still routes service calls via the 009 edge, with full app-on-floci-aws as an enabled follow-on (D0/D6).
- Floci UI → `floci/floci-ui:4500`, needs the socket (D1). | Frontend stack → evolve 009 (D2). | Icons/design →
  Tailwind + Radix + lucide (D3). | Theme → CSS-vars + localStorage + no-flash (D4). | Nav → one-tab-per-service,
  URL-driven (D5). | Function metadata → per-service manifest (D7).
