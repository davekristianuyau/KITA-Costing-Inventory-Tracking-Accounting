# Feature Specification: Service Console — Polished Frontend (Login → Service Workspaces)

**Feature Branch**: `011-service-console-ui`
**Created**: 2026-07-19
**Status**: Draft
**Input**: User description: "create a beautiful frontend using latest available icons, UI and UX we start with the login page after the login the selection of the backend services, floci-aws should be running we deploy all aws resources in floci-aws and i should be able to test and access it in my browser, a button for light mode for dark mode, tabs above navigation, for services a left pane for functions of the service."

## Summary

Turn the current minimal login/placeholder frontend (feature 009) into a **polished, modern web console**. A
user signs in on a redesigned **login page**, then lands in a **service console**: **navigation tabs across the
top**, a way to **select one of their client's backend services**, and — for the selected service — a **left
navigation pane listing that service's functions**. Selecting a function opens a **workspace** where the user
can **view and exercise (test) it** and see the result, right in the browser. A **light/dark theme toggle** is
available everywhere and remembers the choice. The whole experience runs **locally against the client's
environment**, with the client's **AWS resources deployed to the local `floci-aws` emulator** (feature 010) as
the cloud target — everything reachable and testable from the browser with no real cloud.

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

### User Story 3 - Per-service workspace: left pane of functions + test them (Priority: P2)

As a signed-in user working in a service, I see a **left pane listing that service's functions**; when I select a
function, a **workspace** opens where I can **view it and run it (provide inputs, submit, see the result)** — so I
can actually exercise the service from the browser.

**Why this priority**: This is the core working surface — where the console becomes useful, not just pretty. It
depends on US2.

**Independent Test**: Select a service → its functions appear in a left pane; select a function → a workspace
opens; provide any required inputs and run it → a result (or a clear error) is displayed; the backend was
actually called.

**Acceptance Scenarios**:

1. **Given** a selected service, **When** its workspace loads, **Then** a left pane lists that service's
   functions and the main area shows the selected function.
2. **Given** a function, **When** I provide its inputs and run it, **Then** the app calls the service and shows
   the result (data) or a clear, actionable error, with a visible loading state in between.
3. **Given** the workspace, **When** I switch functions in the left pane, **Then** the main area updates to the
   newly selected function without a full reload.

---

### User Story 4 - Runs locally against the floci-aws-backed environment (Priority: P3)

As the operator, I can bring up the client's environment locally — the backend services running and the client's
**AWS resources deployed to `floci-aws`** (feature 010) — and then **use and test the whole console in my
browser** with **no real cloud**, so I can demonstrate the end-to-end product locally.

**Why this priority**: Ties the console to the local, cloud-free environment the rest of the project is built
around; valuable but depends on US1–US3 existing.

**Independent Test**: From a documented startup, `floci-aws` is running with the client's AWS resources deployed,
the backend services are up, and the console is reachable in a browser; a user can log in and exercise a service
function end-to-end using **0** real cloud credentials.

**Acceptance Scenarios**:

1. **Given** the documented local startup, **When** it completes, **Then** `floci-aws` is running with the
   client's AWS resources deployed, the backend is up, and the console is reachable at a local URL in the browser.
2. **Given** the environment is up, **When** I log in and run a service function, **Then** it works end-to-end
   locally with no real cloud credentials or spend.
3. **Given** the environment, **When** the client's deployment is inspected, **Then** the deployed AWS resources
   exist in `floci-aws` (the client's chosen-cloud deployment is represented locally).

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
- **FR-003**: After sign-in, the console MUST present the **backend services available to the signed-in user's
  client** and let the user **select** one to work with.
- **FR-004**: The console MUST provide **navigation tabs across the top** for primary navigation between console
  areas.
- **FR-005**: For a selected service, the console MUST show a **left navigation pane listing that service's
  functions**, and selecting a function MUST open its workspace in the main area (without a full page reload).
- **FR-006**: Each function workspace MUST let the user **view the function, provide any required inputs, run it,
  and see the result** (or a clear error) — i.e. the user can **test** the service from the browser; a **loading
  state** MUST be shown while the call is in flight.
- **FR-007**: The console MUST be **accessible in a standard web browser at a local URL**; consistent with
  feature 009, **only the frontend is host-exposed** and it reaches backend services through the existing edge.
- **FR-008**: The local environment MUST bring up **`floci-aws` running with the client's AWS resources
  deployed** (reusing feature 010) alongside the running backend, and the console MUST function against it using
  **0 real cloud credentials or spend**.
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
- **SC-003**: With the documented local environment up (backend + `floci-aws`), the console is reachable in a
  browser and a user can **sign in and run at least one function of each available service** end-to-end.
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
- **"Functions of a service"** are presented as a **generic function/operation explorer** per service — the left
  pane lists the service's callable operations, and the workspace provides an input form + result view to run
  each — **not** bespoke, fully-designed domain screens for every operation of all five services (that would be
  an ERP's worth of UI and is out of scope here). Rich per-domain screens can come in later features.
- **"floci-aws should be running… test and access it in my browser"** is interpreted as: the **running backend
  services** (the local containers) serve the app traffic the browser exercises, **while `floci-aws` is the
  client's AWS deployment target** where the cloud resources are provisioned and verifiable. Floci emulates the
  AWS **control plane** (ECS/ALB/RDS as mocks) and does **not** run the app containers, so the console's live
  calls hit the running local backend, and `floci-aws` represents the deployed cloud infrastructure. *(This is
  the most consequential interpretation — worth confirming in `/speckit-clarify`.)*
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

- Fully-designed, production-grade domain UIs for every operation of every service (this feature delivers the
  console + a function explorer, not a complete ERP UI).
- Real cloud deployment or serving app traffic from `floci-aws` (Floci mocks compute; real cloud is feature 001).
- GCP/Azure consoles or deployments (AWS/`floci-aws` only here).
- New backend capabilities — the console surfaces existing service functions; it does not add domain features.
- Native mobile apps (responsive web only).
