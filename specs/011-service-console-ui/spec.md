# Feature Specification: Service Console — Polished Frontend (Login → Service Workspaces)

**Feature Branch**: `011-service-console-ui`
**Created**: 2026-07-19
**Status**: Draft
**Input**: User description: "create a beautiful frontend using latest available icons, UI and UX we start with the login page after the login the selection of the backend services, floci-aws should be running we deploy all aws resources in floci-aws and i should be able to test and access it in my browser, a button for light mode for dark mode, tabs above navigation, for services a left pane for functions of the service."

## Summary

Turn the current minimal login/placeholder frontend (feature 009) into a **polished, modern web console** — and
lay the **foundation** for full per-service UIs. This feature (011) delivers the **console shell**: a redesigned
**login page**, a navigation model of **one top tab per backend service**, a **light/dark theme toggle**, and the
**per-service workspace framework** (a service tab opens a **left navigation pane of that service's functions**
beside a **workspace** area). The **complete functionality of each service** is then built out in **its own
follow-on spec**, one per service (starting with **Operations**) — so each service gets a real, full UI rather
than a shallow generic explorer. The whole experience runs **locally**, with the client's **AWS resources
deployed to the local `floci-aws` emulator** (feature 010) as the cloud target and the **Floci UI accessible** —
everything reachable and testable from the browser with **no real cloud**.

## Clarifications

### Session 2026-07-19

- Q: How deep should each service's functions go in the console? → A: **Full functionality per service,
  delivered as SEPARATE per-service specs.** 011 is re-scoped to the **console foundation** (login + shell + one
  tab per service + theme + the per-service workspace framework + the floci-aws-backed local environment + the
  Floci UI). Each service's **complete UI** is a **follow-on spec**, one per service, **starting with Operations**
  then the remaining services (CRM, Procurement, HR, Workflow — order refined per value).
- Q: Given Floci mocks compute, what does "test/access floci-aws in the browser" mean? → A: **It should actually
  work.** Bring up **floci-aws with the host Docker runtime available** (mount `/var/run/docker.sock`, run as
  root) so Floci can run the deployed compute and the app is reachable/testable in the browser, **and the Floci
  UI must be accessible** (the current *"Could not start the Floci UI: … could not reach the container runtime"*
  error is exactly this missing Docker-socket mount). **Whether Floci serves full ECS/ALB app traffic is to be
  verified in `/speckit-plan`** — the earlier "Floci only mocks compute" note was an untested assumption.
- Q: How do the top tabs map to services? → A: **One tab per service** — each backend service is a top tab;
  selecting it shows that service's functions in the left pane. The **tabs ARE the service selection** (no
  separate picker).

## Planned per-service specs (the split)

011 is the foundation. Each service's full UI follows as its own spec, in this intended order (adjustable):
**Operations** (first — the richest domain: catalog, inventory, BOM, production, sales, costing) → **CRM** →
**Procurement** → **HR** → **Workflow**. Each per-service spec fills in its tab's left-pane functions and full
workspaces on top of the 011 framework.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A beautiful, modern sign-in (Priority: P1)

As a user, I open the app and see a clean, modern, professional **login page** (current iconography, clear
layout, obvious affordances) where I sign in with **company + username + password**, with a **light/dark toggle**
available before I even log in — so my first impression is polished and the app feels current.

**Why this priority**: The login page is the first thing every user sees and the entry to everything else. It's
independently valuable and shippable, and it upgrades the placeholder UI from feature 009 into something
presentable.

**Independent Test**: Open the app → a modern login page renders with the three fields, a submit action, and a
theme toggle; toggling switches light/dark instantly; a valid sign-in proceeds, an invalid one shows a clear,
non-revealing error.

**Acceptance Scenarios**:

1. **Given** the app is open, **When** the login page loads, **Then** it presents a modern, uncluttered layout
   with current icons, the company/username/password fields, a primary sign-in action, and a visible theme toggle.
2. **Given** the login page, **When** I toggle light/dark, **Then** the whole page restyles instantly and the
   choice is remembered on reload.
3. **Given** valid credentials, **When** I sign in, **Then** I'm taken into the console; invalid credentials
   show a clear, generic error and I stay on the page.

---

### User Story 2 - Console shell: top tabs + service selection (Priority: P2)

As a signed-in user, I land in a console with **navigation tabs across the top** and can **select which of my
client's backend services** I want to work with — so I can move around the app quickly and choose what to work on.

**Why this priority**: The navigation shell is the frame everything else hangs on; it depends on US1 (being
signed in) and enables US3.

**Independent Test**: After signing in, top tabs are present and switch views; the services available to my
client are listed and selectable; the theme toggle and my identity/client are visible; the theme persists.

**Acceptance Scenarios**:

1. **Given** I'm signed in, **When** the console loads, **Then** top navigation tabs are shown and a set of my
   client's backend services is presented for selection.
2. **Given** the console, **When** I pick a service, **Then** the console shows that service's workspace (US3).
3. **Given** any console view, **When** I toggle the theme or sign out, **Then** the theme applies app-wide (and
   persists), and sign-out returns me to the login page.

---

### User Story 3 - Per-service workspace framework (Priority: P2)

As a signed-in user, when I open a service's tab I see the **per-service workspace framework**: a **left pane**
where that service's functions are listed and a **workspace area** where a selected function opens — so the
console has a consistent, reusable frame that each per-service spec fills with that service's real functionality.

**Why this priority**: This is the reusable frame every follow-on per-service spec builds on. 011 delivers the
framework and proves it end-to-end with **one reference function** wired to the real backend (through the edge);
the **full** function set per service is delivered in that service's own spec.

**Independent Test**: Open a service tab → the left-pane + workspace frame renders; a **reference function** is
present, and running it calls the real backend through the edge and shows the result (or a clear error) with a
loading state; switching left-pane items updates the workspace without a full reload.

**Acceptance Scenarios**:

1. **Given** a service tab, **When** it opens, **Then** the left-pane + workspace frame renders and the
   left pane shows that service's function list (populated fully by the service's own spec; 011 shows at least a
   reference entry).
2. **Given** the reference function, **When** I run it, **Then** the console calls the real service through the
   edge and shows the result or a clear, actionable error, with a visible loading state in between.
3. **Given** the workspace, **When** I switch the selected function in the left pane, **Then** the workspace area
   updates to the newly selected function without a full page reload.

---

### User Story 4 - Runs locally against the floci-aws-backed environment (Priority: P3)

As the operator, I can bring up the client's environment locally — the backend services running and the client's
**AWS resources deployed to `floci-aws`** (feature 010) — and then **use and test the whole console in my
browser** with **no real cloud**, so I can demonstrate the end-to-end product locally.

**Why this priority**: Ties the console to the local, cloud-free environment the rest of the project is built
around; valuable but depends on US1–US3 existing.

**Independent Test**: From a documented startup, `floci-aws` is running **with the Docker runtime available**
(so it can run deployed compute), the client's AWS resources are deployed to it, the **Floci UI opens without
error**, the console is reachable in a browser, and a user can log in and exercise a service function end-to-end
using **0** real cloud credentials.

**Acceptance Scenarios**:

1. **Given** the documented local startup, **When** it completes, **Then** `floci-aws` is running with the host
   Docker socket mounted, the client's AWS resources are deployed to it, the **Floci UI is accessible** (no
   *"could not reach the container runtime"* error), and the console is reachable at a local URL.
2. **Given** the environment is up, **When** I log in and run a service function, **Then** it works end-to-end
   locally with **0** real cloud credentials or spend.
3. **Given** the environment, **When** the client's deployment is inspected (console and/or the Floci UI), **Then**
   the deployed AWS resources exist in `floci-aws` — the client's chosen-cloud deployment is represented, and the
   browser can reach/test the running services.

### Edge Cases

- Not signed in (or session expired) → any console route redirects to the login page.
- A selected service or the backend is unavailable → the console shows a clear "temporarily unavailable" state,
  not a crash or blank screen.
- A function has no result / returns an empty set → an explicit empty state (not a spinner forever).
- A function call fails or times out → a clear, actionable error with a retry, and the rest of the console stays usable.
- The theme toggle is used repeatedly / on first visit → a sensible default (follow the OS preference) and instant, flicker-free switching.
- Small screens (tablet width) → the layout remains usable (tabs and the left pane adapt).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST present a redesigned, modern **login page** (current icon set, clean layout) that
  authenticates with **company + username + password** and preserves feature 009's session behavior (httpOnly
  session, generic non-revealing errors, sign-out).
- **FR-002**: The system MUST provide a **light/dark theme toggle** available on the login page and throughout
  the console; the choice MUST apply app-wide, switch **instantly without a flash**, and **persist** across
  reloads and sessions, defaulting to the operating-system preference on first visit.
- **FR-003**: After sign-in, the console MUST present **one top tab per backend service available to the
  signed-in user's client**; the tabs **are** the service selection (no separate picker).
- **FR-004**: The console MUST provide **navigation tabs across the top** — **one tab per service** — and
  selecting a tab MUST switch to that service's workspace.
- **FR-005**: For the selected service tab, the console MUST show a **left navigation pane listing that service's
  functions** alongside a **workspace area**; selecting a function MUST open it in the workspace **without a full
  page reload**. (011 provides this framework + a reference function; each service's full function set is its own
  follow-on spec.)
- **FR-006**: A function workspace MUST let the user **view the function, provide any required inputs, run it,
  and see the result** (or a clear error) — i.e. **test** the service from the browser — with a **loading state**
  while the call is in flight. 011 MUST demonstrate this end-to-end with at least **one reference function**.
- **FR-007**: The console MUST be **accessible in a standard web browser at a local URL**; consistent with
  feature 009, **only the frontend (and the Floci UI, for inspection)** is host-exposed and the console reaches
  backend services through the existing edge.
- **FR-008**: The local environment MUST bring up **`floci-aws` with the host Docker runtime available** (the
  Docker socket mounted, run as root) so it can run the deployed compute, deploy the client's AWS resources to it
  (reusing feature 010), keep the **Floci UI accessible** (no *"could not reach the container runtime"* error),
  and function using **0 real cloud credentials or spend**. *(Whether Floci serves full app traffic from the
  deployed ECS/ALB is verified in planning.)*
- **FR-009**: The UI MUST meet a modern quality bar: **responsive** down to tablet width, **keyboard-navigable**,
  sufficient **color contrast in both themes**, and consistent use of a **current icon set** and visual style.
- **FR-010**: The console MUST show clear **loading, empty, error, and unauthorized** states for service and
  function interactions (unauthorized → redirect to login; unavailable backend → a clear temporary state).

### Key Entities *(include if feature involves data)*

- **User / Session**: the signed-in user and their httpOnly session (feature 009); determines the client.
- **Client**: the tenant the user belongs to; scopes which services and deployment are shown.
- **Backend Service**: one of the client's services (e.g. operations, HR, CRM, procurement, workflow); has a
  name, an icon, and a set of functions.
- **Service Function**: a callable operation of a service, shown in the left pane; has a label, optional inputs,
  and a result the workspace renders.
- **Theme Preference**: the user's light/dark choice, persisted client-side.
- **Deployment (floci-aws)**: the client's AWS resources provisioned to the local `floci-aws` emulator (feature
  010), representing the client's chosen-cloud deployment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from the login page to running a specific service function in **≤ 3 clicks** after
  sign-in.
- **SC-002**: Toggling light/dark restyles the entire app in **under 200 ms with no flash**, and the choice
  survives a reload.
- **SC-003**: With the documented local environment up (backend + `floci-aws` with the Docker runtime), the
  console is reachable in a browser, the **Floci UI opens with no "container runtime" error**, and a user can
  **sign in and run the reference function through a service tab** end-to-end (full per-service functions arrive
  in their own specs).
- **SC-004**: A user can **invoke a service function and see its result (or a clear error)** from the browser,
  with a visible loading state, in **100%** of attempts against a healthy backend.
- **SC-005**: The UI passes a basic accessibility bar — **WCAG AA color contrast in both themes**, full keyboard
  navigation of tabs/left-pane/forms, and a usable layout down to **768 px** width.
- **SC-006**: The first meaningful console view after sign-in renders in **under 2 seconds** on the local
  environment.
- **SC-007**: The end-to-end local run uses **0** real cloud credentials and incurs **0** cloud spend.

## Assumptions

- **Builds on**: feature 009 (login, identity, tenant-aware edge routing, the React/Vite frontend shell),
  feature 010 (`floci-aws` local AWS deploy of the 001 module), and feature 008 (the containerized backend
  services). This feature **redesigns and extends** that frontend rather than starting over.
- **Each service gets FULL functionality**, but delivered as **separate per-service specs** (see "Planned
  per-service specs"). 011 delivers only the **console foundation + the per-service workspace framework + one
  reference function**; it does **not** implement any service's full UI.
- **`floci-aws` must actually run and be usable**, not just represent a deployment: it is brought up **with the
  host Docker socket mounted** (run as root) so Floci can run the deployed compute, the **Floci UI is
  accessible**, and the browser can reach/test the running services. The earlier "Floci only mocks compute (ECS/
  ALB), so it can't serve the app" note was an **untested assumption and may be wrong** (cf. the corrected GCP
  finding); `/speckit-plan` **verifies empirically** whether Floci serves ECS/ALB app traffic with the Docker
  runtime available, using the Floci docs + the [floci-cli](https://github.com/floci-io/floci-cli).
- **Services shown** default to the current backend set (operations, HR, CRM, procurement, workflow) reachable
  through the edge; the exact per-service function lists derive from each service's existing operations.
- **Icons/visual system**: a modern, open, actively-maintained icon set and a consistent design system; specific
  library choice is an implementation detail for planning.
- **Single language/locale** (English) and standard web performance expectations unless later specified.

## Dependencies

- **Feature 009** — login, identity service, tenant-aware edge, and the existing frontend; this feature evolves them.
- **Feature 010** — `floci-aws` local deploy of the client's AWS resources (the "deployed to floci-aws" part).
- **Feature 008** — the running backend services the console calls.

## Out of Scope

- **The full per-service UIs themselves** — 011 is only the foundation + framework + one reference function;
  each service's complete functionality is delivered in **its own follow-on spec** (see "Planned per-service
  specs"), not here.
- **Real cloud** deployment (that is feature 001); 011 is local-only against `floci-aws`. *(Whether `floci-aws`
  itself serves the deployed app traffic locally is not asserted here — it is verified in planning.)*
- GCP/Azure consoles or deployments (AWS/`floci-aws` only here).
- New backend capabilities — the console surfaces existing service functions; it does not add domain features.
- Native mobile apps (responsive web only).
