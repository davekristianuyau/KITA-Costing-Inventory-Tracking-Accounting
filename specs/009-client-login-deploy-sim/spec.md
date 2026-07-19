# Feature Specification: Client Login & Per-Client Deployment Simulation

**Feature Branch**: `009-client-login-deploy-sim`
**Created**: 2026-07-18
**Status**: Draft
**Input**: User description: "the frontend log-in page, based on 001 specs, the credentials of the user/client will determine what backend it connects to, deployment of the application in production will be based on what the client prefers to use — can we simulate that in docker? we can use [LocalStack] for a local AWS imitation deployment"

## Summary

KITA serves multiple clients, each with an **isolated backend deployment** on the cloud that client prefers
(feature 001). This feature delivers the **frontend login page** (the first real frontend) and a **central
identity service** that authenticates every user, determines which client the user belongs to, and connects
the frontend to **that client's isolated backend** — so a user only ever sees their own client's data. It
also delivers a **local Docker simulation** of the whole model: two isolated client backend stacks (each the
containerized backend from feature 008), the identity service, and the frontend, with **one client's
production deployment imitated locally against a stand-in for AWS** (LocalStack) — proving the
client-preference-driven, per-client deployment works end-to-end before any real cloud is touched.

## Clarifications

### Session 2026-07-18

- Q: How do a user's credentials determine which backend the frontend connects to? → A: A **central identity
  service authenticates every user globally**, records which client they belong to, and after login points the
  frontend at that client's isolated backend. (Data stays isolated per client; only authentication + routing
  are centralized.)
- Q: What does the local Docker simulation cover? → A: **Two isolated client backend stacks** (each = the
  feature-008 containerized backend) + the identity service + the frontend, with **LocalStack as the local
  "AWS" production imitation** for at least one client. GCP/Azure local imitations are out of scope for now.
- Q: Is this the real production behavior or a demo, and where does auth happen? → A: This is the **real
  intended production login + routing behavior**, first delivered as the local Docker/LocalStack simulation;
  **authentication is centralized** in the identity service.
- Q: How does a user identify their client at login, and are usernames unique globally or per-client? → A:
  **Per-client usernames**; the login form collects a **company/client identifier** alongside username and
  password. The same username may exist in different clients; company + username together resolve exactly one
  user.
- Q: How is the session token signed so one client's backend cannot forge tokens for another? → A:
  **Asymmetric** signing — the identity service signs with a **private key**; the edge and every client backend
  **verify with the public key only**. No component other than identity can mint a valid token.
- Q: Where/how is the session kept in the browser? → A: It MUST **survive a browser refresh**, be stored so
  page scripts cannot read it (**httpOnly**), be **encrypted at rest** (an encrypted token, independent of
  transport TLS), and force **re-authentication at least every 90 minutes**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log in and reach my client's backend (Priority: P1)

A user opens the login page, enters their username and password, and signs in. The central identity service
authenticates them, determines their client, and the frontend is connected to that client's isolated backend.
The user then works against **their own client's data only**, and can sign out.

**Why this priority**: This is the core user-facing capability — a working login that lands each user in their
own client's system. Nothing else is usable without it, and it delivers value on its own.

**Independent Test**: With the identity service and one client backend running, enter valid credentials → land
authenticated on that client's backend and read its data; enter invalid credentials → rejected with a clear
message; sign out → returned to the login page and the session no longer works.

**Acceptance Scenarios**:

1. **Given** a provisioned user for client A, **When** they submit valid credentials, **Then** they are
   authenticated and the frontend is connected to client A's backend, showing client A's data.
2. **Given** any user, **When** they submit an invalid username or password, **Then** login is refused with a
   clear, non-revealing error and no backend is contacted on their behalf.
3. **Given** an authenticated user, **When** they sign out, **Then** the session is invalidated and protected
   backend calls no longer succeed until they log in again.
4. **Given** an authenticated session, **When** it expires, **Then** the user is returned to the login page and
   must re-authenticate.

---

### User Story 2 - Tenant isolation: each user reaches only their own backend (Priority: P2)

Two users from two different clients log in from the same login page; each is routed to their **own** isolated
backend, and neither can reach or observe the other client's backend or data.

**Why this priority**: Isolation is the security and correctness heart of a multi-client system handling
financial data — routing the wrong user to the wrong backend, or allowing cross-tenant access, would be a
serious breach. It must be provable independently of the login mechanics.

**Independent Test**: Log in as a client-A user and as a client-B user; confirm each sees only their own
client's data; attempt (with a client-A session) to reach client B's backend and confirm it is refused.

**Acceptance Scenarios**:

1. **Given** users in client A and client B, **When** each logs in, **Then** each is connected to their own
   client's backend and sees only that client's data.
2. **Given** an authenticated client-A session, **When** it is used against client B's backend, **Then** the
   request is refused (no cross-tenant access).
3. **Given** the identity service, **When** it resolves a user, **Then** it maps the user to exactly one client
   and that client's backend endpoint — never more than one.

---

### User Story 3 - Simulate the whole model locally in Docker (Priority: P2)

An operator brings up, on one machine with Docker, the full model: two isolated client backend stacks, the
identity service, and the frontend. They then demonstrate the end-to-end flow — two clients logging in and
each landing in their own isolated backend — with no real cloud involved.

**Why this priority**: The explicit ask ("can we simulate that in docker?"). A reproducible local
demonstration lets the multi-client login/routing model be built, tested, and shown without provisioning real
per-client cloud environments.

**Independent Test**: From a clean checkout, run the documented start command; confirm two client backends, the
identity service, and the frontend all come up; log in as a user from each client and confirm correct,
isolated routing.

**Acceptance Scenarios**:

1. **Given** a clean machine with the container runtime, **When** the operator runs the documented command,
   **Then** two isolated client backend stacks + the identity service + the frontend all reach a healthy state.
2. **Given** the simulation is running, **When** an operator logs in as a user of each client, **Then** the
   full login → routing → client-backend flow works for both, isolated from each other.
3. **Given** the simulation, **When** it is torn down, **Then** each client's data and containers are removed
   cleanly and independently.

---

### User Story 4 - Imitate a client's AWS production deployment locally (Priority: P3)

For a client whose preferred cloud is AWS, an operator "deploys" that client's backend against a **local
stand-in for AWS** (LocalStack) instead of the real cloud, and the login flow reaches that client's
locally-imitated production deployment — proving the client-preference-driven deployment path end-to-end
without real cloud credentials or spend.

**Why this priority**: The second explicit ask ("[LocalStack] for a local AWS imitation deployment"). It closes
the loop between "client prefers cloud X" (feature 001) and a runnable, inspectable local proof, de-risking the
real deployment.

**Independent Test**: Configure one simulated client with an AWS preference; deploy its backend against the
local AWS imitation; log in as that client's user and confirm the frontend reaches the client's
locally-imitated production deployment; confirm no real AWS account is used.

**Acceptance Scenarios**:

1. **Given** a client whose preference is AWS, **When** its backend is deployed against the local AWS imitation,
   **Then** the deployment completes using only the local imitation (no real cloud credentials).
2. **Given** that locally-imitated deployment, **When** the client's user logs in, **Then** the frontend is
   routed to that deployment and works end-to-end.
3. **Given** the deployment model, **When** a client's cloud preference is recorded, **Then** it determines the
   deployment target (here, the AWS imitation) — mirroring the feature-001 client-choice model.

---

### Edge Cases

- **Unknown user / no client mapping**: identity service cannot resolve the user to a client → login refused
  with a clear, non-revealing message.
- **Client backend unreachable**: user authenticates but their client's backend is down → clear "temporarily
  unavailable" state, not a crash or a wrong-backend fallback.
- **Repeated failed logins**: the same account is hammered with wrong passwords → throttled/limited to resist
  brute force.
- **Session expiry / revocation mid-session**: after the 90-minute session lifetime (or a sign-out/revocation),
  an in-flight action is rejected and the user is sent back to login to re-authenticate.
- **Stolen/replayed session against another tenant**: a client-A session presented to client B's backend is
  rejected (isolation holds even with a valid-looking session; asymmetric verification means no backend can
  forge one either).
- **Two clients, same username**: the same username in two clients is disambiguated by the submitted company
  identifier — company + username resolve exactly one user, with no cross-tenant match.
- **Simulation port/paths collide**: two client stacks on one host don't conflict; each is independently
  addressable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST present a login page where a user submits a **company/client identifier**, a
  username, and a password, and receives a clear success or failure result, with visible loading and error
  states.
- **FR-002**: A central identity service MUST authenticate submitted credentials and, on success, establish an
  authenticated session for the user.
- **FR-003**: The identity service MUST resolve each authenticated user to exactly one client and that client's
  backend endpoint.
- **FR-004**: On successful login, the frontend MUST be connected to the resolving user's client backend, and
  all subsequent application requests MUST go to that backend only.
- **FR-005**: A user MUST only ever be able to reach their own client's backend and data; cross-tenant access
  MUST be refused (tenant isolation).
- **FR-006**: A client's backend MUST accept an authenticated session only when it belongs to that client, and
  reject sessions belonging to any other client.
- **FR-007**: Users MUST be able to sign out, which invalidates the session so protected backend calls no
  longer succeed until re-authentication.
- **FR-008**: Invalid credentials, unknown users, and expired sessions MUST be rejected with clear,
  non-revealing messages and MUST NOT grant backend access.
- **FR-009**: Repeated failed authentication attempts MUST be throttled/limited to resist brute-force attacks.
- **FR-010**: Credentials MUST be transmitted over an encrypted channel and MUST NEVER be stored or logged in
  plaintext anywhere (frontend, identity service, or logs).
- **FR-011**: Each client MUST have an isolated backend deployment; one client's data MUST NOT be reachable from
  another client's deployment (consistent with feature 001 isolation).
- **FR-012**: Each client MUST have a recorded **cloud/deployment preference** that determines its production
  deployment target (consistent with the feature-001 client-choice model).
- **FR-013**: The system MUST provide a local, single-command Docker simulation that brings up at least **two**
  isolated client backend stacks, the identity service, and the frontend, healthy and independently
  addressable.
- **FR-014**: The simulation MUST demonstrate the full login → routing → client-backend flow for users of both
  simulated clients, with correct isolation between them.
- **FR-015**: The simulation MUST deploy at least one client's backend against a **local imitation of AWS**
  (standing in for that client's preferred cloud) using no real cloud credentials or spend.
- **FR-016**: When a user's client backend is unreachable, the system MUST surface a clear
  temporarily-unavailable state rather than failing silently or routing elsewhere.
- **FR-017**: Tearing down the simulation MUST remove each client's containers and data cleanly and
  independently.
- **FR-018**: The identity service MUST hold only what it needs to authenticate and route (user credentials and
  the user→client→endpoint mapping) — never a client's business/financial data.
- **FR-019**: Usernames MUST be unique **within a client** (the same username MAY exist in different clients);
  the submitted company identifier + username together MUST resolve to exactly one user.
- **FR-020**: Session tokens MUST be signed **asymmetrically** — the identity service signs with a private key,
  and the edge and every client backend verify with the public key only. No component other than the identity
  service MUST be able to mint a valid token (so one client's backend cannot forge another client's token).
- **FR-021**: The browser session MUST survive a page refresh, MUST be stored so page scripts cannot read it
  (httpOnly), MUST keep the session token **encrypted at rest** (independent of transport encryption), and MUST
  require re-authentication at least every **90 minutes**.

### Key Entities *(include if feature involves data)*

- **User**: a person who signs in; belongs to exactly one client; **username unique within that client**; has
  credentials (never stored in plaintext).
- **Client (tenant)**: an organization with a **company identifier used at login**, its own isolated backend
  deployment, and a recorded cloud/deployment preference.
- **Central identity service**: authenticates users, maps user → client → backend endpoint, issues/validates
  sessions; holds no client business data.
- **Session/credential token**: proof of authentication carrying the user's client identity; **asymmetrically
  signed** by the identity service (verified with a public key), **encrypted at rest** in the browser, valid for
  at most 90 minutes; accepted only by that client's backend.
- **Client backend deployment**: an isolated stack (per features 008/001) reachable via its own gateway.
- **Login page**: the frontend entry screen (username/password, states, sign-out).
- **Local simulation environment**: the Docker composition of two client stacks + identity service + frontend +
  the local AWS imitation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can log in and reach their own client's backend in under 30 seconds and no more than 3
  on-screen steps.
- **SC-002**: 100% of authenticated sessions are routed to the correct client's backend; **0** cross-tenant
  accesses succeed across the test suite.
- **SC-003**: Invalid credentials, unknown users, and expired/foreign sessions are rejected 100% of the time;
  sessions require re-authentication at least every 90 minutes.
- **SC-008**: No component other than the identity service can produce a token accepted by any backend (a token
  minted with a client backend's own keys is rejected everywhere) — verified by test.
- **SC-004**: From a clean checkout, an operator brings up the full local simulation (two isolated client
  backends + identity + frontend) with a single command in under 15 minutes, and can log in as a user of each
  client into their own isolated backend.
- **SC-005**: At least one client's backend is deployed and reachable through the login flow via the local AWS
  imitation, using **0** real cloud credentials.
- **SC-006**: When a client backend is down, 100% of that client's login attempts show a clear
  temporarily-unavailable state (never a wrong-backend or silent failure).
- **SC-007**: A security review finds no plaintext credential stored or logged in the frontend, identity
  service, or logs.

## Assumptions

- **"focci" = LocalStack**: the "local AWS imitation" is taken to be LocalStack (or an equivalent local AWS
  emulator); the exact tool is confirmed in the plan.
- **Frontend is greenfield here**: the frontend is currently the feature-002 empty scaffold; this feature
  delivers the first real frontend surface — the login page and the post-login routing shell — not the whole
  application UI.
- **Builds on 001 + 008**: per-client isolated deployment and client cloud-choice come from feature 001; each
  client backend is the containerized stack from feature 008. This feature adds the login page, the central
  identity/routing plane, and the local multi-client + AWS-imitation simulation.
- **Centralized authentication is a new shared component**: unlike per-deployment auth, a single identity
  service authenticates across clients and routes them; **data isolation per client is preserved** — only
  authentication and endpoint resolution are centralized.
- **Two demo clients**: the simulation uses two representative clients (e.g., one preferring AWS) to prove
  routing and isolation; the model generalizes to more.
- **Users are provisioned, not self-registered**: user self-signup, password reset, and email verification
  flows are assumed out of scope unless later specified.

## Dependencies

- Feature 001 (per-client isolated deployment; client cloud preference; gateway-fronted, private backends).
- Feature 008 (containerized backend stack used as each client's backend).
- A container runtime + multi-container orchestration, and a local AWS emulator for the imitation deployment.
- An environment-scoped secrets/configuration source for credentials and tokens (no secrets in the repo).

## Out of Scope

- Local imitations of **GCP and Azure** deployment targets (this feature covers the AWS imitation only).
- Real cloud provisioning/deployment (that is feature 001; here it is imitated locally).
- In-application role/permission management beyond authenticating the user and routing to their backend
  (fine-grained authorization stays within the backend services).
- User self-registration, password reset, MFA, and SSO/social login (possible later features).
- Building the rest of the frontend application UI beyond the login page and the post-login routing shell.
