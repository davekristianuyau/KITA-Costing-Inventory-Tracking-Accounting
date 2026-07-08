# Feature Specification: Application Source Code Scaffolding

**Feature Branch**: `002-source-scaffold`
**Created**: 2026-07-08
**Status**: Draft
**Input**: User description: "scaffolding the source code, this will be in JS, react, vue if possible, and java springboot and microservices, frontend and backend runs in 1 container"
**Resolved decisions**: Frontend = React (chosen to share language/patterns with a planned
React Native Android app); Backend = true microservices, each service and the frontend in
its own container (multiple containers).

## Overview

This feature establishes the initial application source-code skeleton for KITA (costing,
inventory tracking, and accounting). It delivers a runnable, testable, multi-service project
that a developer can build from a clean checkout: a React web frontend and a set of Java
Spring Boot microservices, each packaged as its **own container image** and orchestrated to
run together locally. These images are the artifacts consumed by the multi-cloud delivery
pipeline (feature `001-multi-cloud-cicd`). This feature delivers structure, tooling, and a
thin vertical slice across the services — not the business logic of costing, inventory, or
accounting.

> **Cross-feature note**: The microservices/multi-container decision supersedes the earlier
> "frontend and backend in 1 container" phrasing and requires feature `001-multi-cloud-cicd`
> to be revised to deploy a set of services (multiple images + gateway/ingress + internal
> networking) rather than a single image. Flagged as a follow-up on feature 001.

## Clarifications

### Session 2026-07-08

- Q: How many services does the scaffold stand up initially? → A: Gateway + frontend +
  one generic reference microservice only; real domains (costing/inventory/accounting)
  are added later via the documented "add a service" pattern.
- Q: Does the reference service include real database persistence? → A: Yes — it
  connects to PostgreSQL with versioned migration tooling and one sample table, and
  reads/writes it (full persistence slice), establishing the migration pattern.
- Q: How is the gateway implemented? → A: Two layers — Nginx at the edge (serves the
  built React static assets, HTTP caching/compression, reverse-proxies `/api/*`) and a
  dedicated Spring Cloud Gateway service for backend API routing (and future auth,
  rate limiting, circuit breaking). TLS/DNS/custom domain are handled by feature 001's
  cloud load balancer, so Nginx does not terminate TLS.
- Q: What form does the frontend↔backend interface contract take? → A: REST/JSON over
  HTTP with an OpenAPI specification as the source of truth (contract-first), used to
  generate typed clients and contract tests.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build and run all services from a clean checkout (Priority: P1)

A developer clones the repository and, with a single documented command, builds and runs the
full system locally. The frontend and the backend microservices come up together and are
networked; the frontend loads in a browser and successfully reaches the backend through the
gateway, showing a live "backend healthy" status.

**Why this priority**: A scaffold that a new contributor cannot build and run on day one has
no value. This is the foundational, demonstrable slice everything else builds on.

**Independent Test**: On a clean machine with only the documented prerequisites, run the
documented build-and-run command; confirm all services start, are networked, and the frontend
renders showing a healthy backend status fetched via the gateway.

**Acceptance Scenarios**:

1. **Given** a clean checkout and documented prerequisites, **When** the developer runs the
   documented build command, **Then** every service and the frontend build successfully with
   no manual edits.
2. **Given** a successful build, **When** the developer runs the documented run command,
   **Then** all services and the frontend start, are networked, and the frontend is reachable
   in a browser showing a healthy backend status.
3. **Given** the running system, **When** each service's health endpoint is called (directly
   or via the gateway), **Then** each responds successfully and reports its version/status.

---

### User Story 2 - Each service builds into its own container image (Priority: P1)

A developer runs documented commands that produce one container image **per service** (the
frontend and each backend microservice). Running the produced set of images (via the
documented local orchestration) serves the frontend and exposes each service's health
endpoint.

**Why this priority**: The scaffold feeds the delivery pipeline (feature 001), which deploys
these images. Without per-service images the system cannot be deployed.

**Independent Test**: Build the images from the repository, run them with the documented
orchestration and no extra files, and confirm the frontend and each backend service's health
endpoint are reachable.

**Acceptance Scenarios**:

1. **Given** the repository, **When** the developer runs the documented image-build command,
   **Then** one image is produced per service (frontend + each backend microservice).
2. **Given** the built images, **When** they are run together via the documented
   orchestration, **Then** the frontend is served and every backend service's health endpoint
   responds.
3. **Given** a built image, **When** it is inspected, **Then** it carries an immutable version
   tag and exposes the documented port and health endpoint expected by feature 001.

---

### User Story 3 - Backend split into independent microservices (Priority: P2)

The backend is structured as independently developable, independently deployable Spring Boot
microservices for KITA's business domains (e.g., costing, inventory, accounting) fronted by a
gateway, with one reference domain microservice implemented end to end (a status/sample
endpoint) to prove the pattern and the service-to-service/gateway path.

**Why this priority**: KITA's domains must evolve and deploy independently. Establishing the
service boundaries, gateway routing, and a working reference service early prevents a tangled
codebase; it depends on the system first building and running (US1/US2).

**Independent Test**: Call the reference service's endpoint through the gateway and get a
correct response; confirm a second microservice can be added following the documented pattern
and routed through the gateway without modifying unrelated services.

**Acceptance Scenarios**:

1. **Given** the running backend, **When** the reference service's endpoint is called through
   the gateway, **Then** it returns the expected response independently of other services.
2. **Given** the service structure, **When** a developer adds a new domain microservice
   following the documented pattern, **Then** it builds into its own image and is routed
   through the gateway without changing existing services' code.

---

### User Story 4 - Frontend application shell with backend integration (Priority: P2)

The React frontend provides an application shell — navigation, a landing view, and at least
one view that fetches and displays data from the backend reference service through the gateway
— plus the build tooling to produce optimized static assets served from the frontend image.

**Why this priority**: Demonstrates the end-to-end path (UI → gateway → service → response) and
gives future feature work a place to add screens. Depends on the backend reference service (US3).

**Independent Test**: Load the frontend, navigate to the data view, and confirm it displays
data retrieved from the backend reference service via the gateway.

**Acceptance Scenarios**:

1. **Given** the running system, **When** a user opens the frontend, **Then** an application
   shell with navigation and a landing view renders.
2. **Given** the frontend, **When** the user opens the data view, **Then** it shows data
   fetched from the backend reference service, and shows a clear error state if the backend is
   unavailable.

---

### User Story 5 - Test and quality-gate scaffolding (Priority: P3)

The project ships with a working automated test setup (frontend and each backend service), and
code formatting/linting configured, all runnable through documented commands — so the
constitution's test-first workflow and automated quality gates apply from the first commit.

**Why this priority**: Aligns with the project constitution (TDD and Automated Quality Gates).
Essential for healthy development but does not block a first runnable build.

**Independent Test**: Run the documented test command and the documented lint command across
the repo; both execute against the scaffold and report results, with at least one passing
sample test per service and for the frontend.

**Acceptance Scenarios**:

1. **Given** the scaffold, **When** the developer runs the test command, **Then** frontend and
   each backend service's sample tests execute and report pass/fail.
2. **Given** the scaffold, **When** the developer runs the lint/format check, **Then** it
   reports style compliance and fails on a deliberately malformed sample.

---

### Edge Cases

- What happens when the backend (or a single microservice) is unavailable while the frontend
  loads? The frontend must show a clear, non-crashing error/empty state.
- What happens when the gateway cannot reach a downstream service? The gateway must return a
  clear error and remain healthy for the services that are up.
- What happens when required configuration (service ports, gateway routes, service URLs,
  database connection) is missing at startup? The affected service must fail fast with a clear
  message naming what is missing.
- How does local orchestration behave if one service fails to start? It must surface that
  service as unhealthy rather than appearing fully running.
- How are service versions kept compatible when deployed together, so a running system never
  mixes incompatible service versions (independent images, coordinated release)?
- How does a developer's differing local prerequisite version get detected and reported rather
  than failing obscurely?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST build every service and the frontend successfully from a
  clean checkout using a single documented command, with no manual file edits required.
- **FR-002**: The system MUST run locally via a single documented command that starts and
  networks the frontend and all backend microservices together.
- **FR-003**: Each backend microservice MUST expose a health/status endpoint reporting success
  and a version identifier; the gateway MUST expose an aggregate health view.
- **FR-004**: The React frontend MUST render an application shell and successfully retrieve and
  display data from the backend through the gateway.
- **FR-005**: The build MUST produce one container image **per service** (the frontend and each
  backend microservice), each independently runnable.
- **FR-006**: Each container image MUST expose a documented port and health endpoint matching
  what feature `001-multi-cloud-cicd` will deploy, and MUST carry an immutable version tag.
- **FR-007**: The backend MUST be organized as independently developable and independently
  deployable microservices behind a gateway, with one reference microservice implemented end
  to end.
- **FR-008**: A developer MUST be able to add a new domain microservice by following a
  documented pattern — building its own image and routing through the gateway — without
  modifying unrelated services.
- **FR-009**: Every inter-service and frontend-to-backend interaction MUST use REST/JSON over
  HTTP described by a versioned OpenAPI specification (contract-first) that serves as the
  single source of truth, so services cannot drift silently.
- **FR-009a**: The OpenAPI specification MUST be usable to generate the frontend's typed API
  client and to drive contract tests validating that services conform to it.
- **FR-010**: The project MUST include runnable automated tests for the frontend and each
  backend service, with at least one passing sample test each.
- **FR-011**: The project MUST include configured code formatting and linting runnable via a
  documented command and enforceable as a quality gate.
- **FR-012**: Each service MUST fail fast with a clear message when required startup
  configuration is missing.
- **FR-013**: The frontend MUST present a clear error/empty state when the backend or a needed
  service is unavailable, without crashing.
- **FR-014**: Configuration (service ports, gateway routes, service locations, data source)
  MUST be externalized (not hard-coded) so the same images run unchanged across environments
  (supports feature 001).
- **FR-015**: All prerequisites and every build/run/test/lint command MUST be documented so a
  new developer can go from checkout to a running multi-service system by following the
  documentation only.
- **FR-016**: Services released together MUST be version-compatible; the scaffold MUST define
  how a coordinated set of service image versions is expressed so a running system never mixes
  incompatible versions.
- **FR-017**: A dedicated API gateway (Spring Cloud Gateway) MUST route requests to the correct
  backend microservice and serve as the single backend entry point; it is the intended home for
  future cross-cutting concerns (auth, rate limiting, circuit breaking).
- **FR-017a**: An Nginx edge layer MUST serve the built React static assets, apply HTTP
  caching and compression, and reverse-proxy `/api/*` requests to the API gateway. It MUST NOT
  terminate TLS (TLS/DNS/custom domain are provided by feature 001's cloud load balancer).
- **FR-018**: The reference microservice MUST connect to a PostgreSQL database and MUST include
  versioned, repeatable schema migration tooling that creates at least one sample table on
  startup/deploy, per the constitution's migration discipline.
- **FR-019**: The reference microservice MUST demonstrate a persisted read/write against the
  sample table, and the frontend round-trip (US4) MUST surface data that originates from the
  database (not a hard-coded stub).

### Key Entities *(include if feature involves data)*

- **Service Image**: A single deployable container image for one service (the frontend or a
  backend microservice); carries an immutable version tag and a documented port + health
  endpoint.
- **Microservice**: An independently developable and deployable backend unit representing a
  business area (e.g., costing, inventory, accounting); has a defined boundary, its own image,
  and a health endpoint.
- **API Gateway**: The dedicated Spring Cloud Gateway service that routes requests to backend
  microservices, aggregates health, and hosts future cross-cutting concerns (auth, rate
  limiting). Its own container image.
- **Edge / Nginx layer**: Serves the built React static assets and applies HTTP caching and
  compression, reverse-proxying `/api/*` to the API Gateway. Packaged as the frontend's
  container image; does not terminate TLS.
- **Reference Microservice**: The one fully implemented example microservice (status/sample
  endpoint) demonstrating the service pattern and gateway routing end to end.
- **Frontend Shell**: The React application providing navigation and views, including a view
  that consumes the reference microservice; built to static assets and served by the Edge/Nginx
  layer in its own container image.
- **Interface Contract**: A versioned OpenAPI specification (REST/JSON) that is the source of
  truth for requests/responses between the frontend, gateway, and services; drives generated
  clients and contract tests.
- **Release Set**: The coordinated set of service image versions known to be compatible and
  deployed together (addresses cross-service version consistency).
- **Schema Migration**: A versioned, repeatable database change applied by the reference
  microservice's migration tooling; creates the sample table and models how future services
  evolve their schema.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new developer can go from a clean checkout to a running multi-service system by
  following the documentation, in under 45 minutes, with no undocumented steps.
- **SC-002**: The documented build command succeeds from a clean checkout on a supported
  environment 100% of the time (no manual fix-ups).
- **SC-003**: A documented build produces one deployable image per service, and the documented
  orchestration runs them together serving the frontend and every service's health endpoint.
- **SC-004**: The running system demonstrates a full round trip: the frontend displays data
  fetched from the backend reference microservice through the gateway.
- **SC-005**: A developer can add a new domain microservice following the documented pattern
  without editing any existing service's code, verified by adding a throwaway service.
- **SC-006**: The documented test and lint commands run successfully across the repo, with at
  least one passing sample test for the frontend and each backend service.
- **SC-007**: When a backend service is stopped, the frontend shows a clear error state rather
  than a blank or crashed page, 100% of the time.
- **SC-008**: The produced images are accepted and deployable by feature 001's pipeline (once
  revised for multi-service), with correct ports, health endpoints, and immutable tags.
- **SC-009**: On a clean database, running the reference service applies its migrations to
  create the sample table and completes a persisted read/write; re-running the migrations is
  safe (no error, no duplicate schema).

## Assumptions

- **Frontend framework**: React (single-page application). Chosen so the web app shares
  language, patterns, and tooling with a planned React Native Android app (a separate future
  feature). Vue is not used.
- **Backend**: Java + Spring Boot, structured as true microservices, each independently
  deployable in its own container image, fronted by a gateway.
- **Multiple containers**: The system runs as multiple containers (Nginx/frontend, API
  gateway, each backend service, and a local PostgreSQL), orchestrated locally by **Docker
  Compose** (the documented "single command"). This supersedes the earlier "1 container"
  phrasing. (Low-risk default; not asked during clarification.)
- **Service routing/config**: Backend service routes are configured statically in the API
  gateway via externalized configuration/environment variables; no dynamic service-discovery
  server (e.g., Eureka) is introduced at scaffold stage (YAGNI). (Low-risk default; not asked.)
- **Feature 001 impact**: Feature `001-multi-cloud-cicd` currently deploys a single image per
  app and MUST be revised to deploy a multi-service set (multiple images, gateway/ingress,
  internal networking). Tracked as a follow-up; this spec does not modify feature 001.
- **Scope is a skeleton**: This feature delivers project structure, gateway, one reference
  microservice, a thin React UI slice, and build/run/test tooling — not costing/inventory/
  accounting business logic (future features).
- **Database**: The scaffold uses a relational database (PostgreSQL, matching feature 001's
  managed database). The reference microservice connects to it, ships versioned migration
  tooling and one sample table, and performs a read/write against it — establishing the
  persistence and migration pattern future services follow.
- **Android app**: A future React Native Android application is anticipated but is out of scope
  for this feature; the React choice is made partly to support it.

## Dependencies

- Depends on (and will require revision of) the deployment contract from feature
  `001-multi-cloud-cicd` to support multiple service images, a gateway/ingress, and
  service-to-service networking.
- A container build tool, a local multi-container orchestration tool, and documented
  language/runtime prerequisites available to developers.

## Out of Scope

- Business logic for costing, inventory, or accounting (future features).
- The React Native Android application (future feature).
- Authentication/authorization implementation (separate feature).
- CI/CD pipeline definition and the multi-service deployment revision (owned by feature 001).
- Production data modeling and migrations beyond what the reference slice needs.
