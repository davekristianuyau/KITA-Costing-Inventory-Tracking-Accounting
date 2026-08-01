# Feature Specification: Local Production-Replica Deployment

**Feature Branch**: `019-local-production-deploy`
**Created**: 2026-08-01
**Status**: Draft
**Input**: User description: "Deploy the system with Terraform to the local AWS service using Floci AWS. The skill should deploy seamlessly and I should be able to manually test the system in my browser. Trigger: `/deploy-local`. Output: launching the system, verify the system is healthy."

## Context

The project already has three ways to run KITA locally, and none of them exercises the path that will
deploy to a real cloud:

- `docker-compose.yml` — an explicitly unauthenticated dev stack.
- `sim/sim-up.sh` — an authenticated, multi-client stack, but wired by Compose, not by the deployment code.
- `sim/cloud-deploy/deploy-check.sh` — applies the **real** infrastructure module against the local cloud
  emulator, then **destroys it**. It proves the definitions are valid; it never leaves a usable system,
  and it covers only 2 of the 10 deployable units.

So the artifact that will actually create production has never been observed *running the product*. This
feature closes that gap: one command deploys the whole system through the real infrastructure definitions
onto the local cloud emulator, and leaves it running and browsable.

The value is confidence. A deployment defect found here costs minutes; the same defect found during a real
cloud rollout costs an incident, and this project has no second reviewer to catch it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bring the whole system up and use it (Priority: P1)

As the developer, I run a single command and, minutes later, open a browser to a working KITA system —
sign in, click through the service consoles, create records — with every service deployed by the same
infrastructure code that will deploy to a real cloud.

**Why this priority**: This is the feature. Everything else refines or protects it. Delivered alone it
replaces a manual multi-step bring-up and gives the first end-to-end observation of the deployment path
actually running the product.

**Independent Test**: Run the command on a machine with nothing running, then complete a real task in the
browser (sign in, create a record, read it back). No other story needs to exist.

**Acceptance Scenarios**:

1. **Given** a clean machine with the container runtime available, **When** the deploy command is run,
   **Then** the entire system is deployed and the command prints a URL that serves the sign-in page.
2. **Given** the deployed system, **When** the developer signs in and performs a governed action, **Then**
   it succeeds and is visible on re-read — proving the services, the database and the identity path work
   together.
3. **Given** the deployed system, **When** the developer inspects how traffic reaches the application,
   **Then** requests arrive through the same public entry point the production design uses, not a
   direct-to-container shortcut.
4. **Given** the deployment finishes, **When** the developer looks for what to do next, **Then** the
   command has already printed the entry URL and the credentials needed to sign in.

---

### User Story 2 - Refuse to report success on a broken system (Priority: P2)

As the developer, I need the command to tell me the truth. If the system came up but something is silently
broken, it must fail and say what — never print a URL and claim success.

**Why this priority**: The existing check verifies a deployment by asking the infrastructure tool which
resources exist. During research for this feature, **two separate defects were found that this style of
check would have passed**: an internal name that resolved nowhere, and a public entry path that appeared
fully configured but served no traffic. Both would have produced a "successful" deployment of an unusable
system. A deploy command that can lie is worse than none, because it turns a loud failure into a silent
one.

**Independent Test**: Deliberately break one internal dependency, run the deploy, and confirm the command
fails and names the broken dependency instead of reporting success.

**Acceptance Scenarios**:

1. **Given** all services report healthy individually, **When** verification runs, **Then** it also
   performs at least one action requiring one service to call another, and only then reports success.
2. **Given** an internal service address that resolves to nothing, **When** verification runs, **Then** the
   command fails and identifies the unreachable dependency.
3. **Given** a service that never becomes healthy within the allowed time, **When** the wait expires,
   **Then** the command fails, names the service, and points to where its logs can be read.
4. **Given** verification fails, **When** the developer inspects the machine, **Then** the deployed system
   is left running for diagnosis rather than torn down automatically.

---

### User Story 3 - Deploy on the compute model the business will actually pay for (Priority: P3)

As the owner, I need the local deployment to use the same compute model production will use, so what I
validate is what I will run — and that model must be the cost-appropriate one for a startup rather than
the premium serverless option.

**Why this priority**: The infrastructure module currently targets the serverless compute mode, which was
never the intended production choice on cost grounds. Validating a compute model we do not intend to buy
gives false confidence. Separable from P1 — the system can come up first and the compute model be
corrected after — but it must land before any real cloud rollout.

**Independent Test**: Inspect the deployed workloads and confirm they run under the intended compute model;
separately confirm the infrastructure code still describes a complete, deployable arrangement for a real
cloud.

**Acceptance Scenarios**:

1. **Given** the infrastructure code, **When** the system is deployed locally, **Then** workloads run under
   the intended cost-appropriate compute model rather than the premium serverless one.
2. **Given** that model requires capacity to be provisioned in a real cloud but not in the emulator,
   **When** the change is made, **Then** the infrastructure code provisions that capacity and the gap is
   recorded as **not verifiable locally**.
3. **Given** the database is reachable on a non-default port, **When** services read their connection
   settings, **Then** they connect successfully — the port is taken from the created database, never
   assumed.

---

### User Story 4 - Repeatable: tear down and run again (Priority: P4)

As the developer, I need to tear the environment down completely and run the deploy again with no manual
cleanup, so this becomes a habit rather than an occasional ordeal.

**Why this priority**: Determines whether the command gets used daily or abandoned. Valuable only once P1
works, and the deploy is still useful without it — manual cleanup is possible, just tedious.

**Independent Test**: Deploy, tear down, deploy again; confirm the second run succeeds with no manual
intervention and no leftovers from the first.

**Acceptance Scenarios**:

1. **Given** a deployed system, **When** teardown is run, **Then** every resource and container it created
   is removed, and unrelated containers on the machine are untouched.
2. **Given** a previous run was interrupted partway, **When** the deploy runs again, **Then** it converges
   to a working system without manual cleanup first.
3. **Given** the emulator restarts and loses part of its recorded state, **When** the deploy runs again,
   **Then** it re-establishes what is missing rather than failing on already-exists or not-found errors.
4. **Given** a rebuilt application image, **When** the deploy is re-run, **Then** the running system serves
   the new build.

---

### Edge Cases

- **A required host port is already taken** (by a previous run, or by the emulator's own auxiliary
  containers) — fail with the specific port and what holds it, not a generic bind error.
- **The container runtime is unreachable or not configured the way the emulator requires** — detect in
  preflight with a fix instruction, rather than surfacing as a mid-deploy crash.
- **An application image fails to build** — stop before touching any infrastructure.
- **A workload is replaced after deployment** (crash, scale, redeploy) — any local-only addressing
  substitute applied after placement is lost; the system must reapply it or make the resulting failure
  obvious rather than degrading silently.
- **The emulator loses recorded state on restart while its backing stores persist** — steps that create
  records must tolerate both "already exists" and "unexpectedly missing".
- **Real cloud credentials are present in the environment** — the command must target only the local
  emulator and never a real account.
- **Verification passes but the developer's browser cannot reach the entry point** (host versus container
  networking) — the printed URL must be reachable from the developer's own machine, and that must be
  asserted rather than assumed.

## Requirements *(mandatory)*

### Functional Requirements

**Deployment**

- **FR-001**: A single command MUST deploy the complete KITA system — every deployable unit, including the
  user interface and the sign-in path — onto the local cloud emulator.
- **FR-002**: The deployment MUST use the same infrastructure definitions that deploy to a real cloud. A
  parallel local-only copy of those definitions is not acceptable.
- **FR-003**: The deployment MUST NOT require or use real cloud credentials, and MUST NOT create resources
  in any real cloud account.
- **FR-004**: Application images MUST be published to and consumed from an image registry, matching how
  production distributes builds, rather than being referenced from the local build cache.
- **FR-005**: The system MUST run against a real database engine with the project's schema migrations
  applied — not a stand-in, and not an unmigrated store.
- **FR-006**: Database connection settings MUST be derived from the database actually created, including
  its port, never from assumed defaults.
- **FR-007**: Browser traffic MUST enter through the same public entry point the production design uses.
- **FR-008**: Services MUST address one another by the same internal names they use in production, with no
  change to their deployed configuration.
- **FR-009**: Where a capability is absent from the emulator and a local-only substitute is required, that
  substitute MUST be confined to the deployment tooling, MUST NOT alter deployed application
  configuration, and MUST be listed in one documented place.

**Verification**

- **FR-010**: The command MUST verify the deployment by exercising real traffic against the running system.
  Confirming that infrastructure resources exist is explicitly insufficient.
- **FR-011**: Verification MUST include at least one operation requiring one service to call another, so
  internal addressing is proven rather than assumed.
- **FR-012**: Verification MUST confirm the printed entry URL is reachable from the developer's own machine.
- **FR-013**: The command MUST report success only when every verification step passes; any failure MUST
  produce a non-success result naming the failing component.
- **FR-014**: On failure the command MUST leave the environment running for diagnosis and MUST tell the
  developer where to read the failing component's logs.
- **FR-015**: The command MUST bound how long it waits for health and fail with a clear timeout rather than
  hanging indefinitely.

**Lifecycle**

- **FR-016**: The command MUST be safe to re-run: a second run against an existing or partially-created
  environment MUST converge without manual cleanup.
- **FR-017**: Teardown MUST remove everything the deployment created and MUST NOT remove unrelated
  containers, images, or volumes on the machine.
- **FR-018**: Preflight checks MUST verify prerequisites — container runtime reachable and configured as
  required, needed ports free, build tooling present — and fail with actionable messages before any
  resource is created.
- **FR-019**: On success the command MUST print the entry URL and the credentials needed to sign in.

**Production parity**

- **FR-020**: Deployed workloads MUST use the cost-appropriate compute model intended for production, not
  the premium serverless option.
- **FR-021**: Where the intended compute model requires capacity provisioning that a real cloud needs and
  the emulator does not, the infrastructure code MUST still define it.
- **FR-022**: Every production behaviour that cannot be validated locally MUST be recorded as an explicit,
  enumerated gap, so local success is never mistaken for production readiness.
- **FR-023**: Secrets and credentials used by the local deployment MUST NOT be committed to the repository
  and MUST NOT appear in command output or logs.

### Key Entities

- **Deployable unit**: one application service or the user interface — the port it listens on, whether it
  is publicly reachable, its health endpoint, and the image carrying its build.
- **Release Set**: the versioned collection of deployable units deployed together, so the running system is
  a known consistent combination rather than an accident of timing.
- **Deployment environment**: a named client + environment pair scoping every created resource and every
  internal name, keeping one run isolated from another.
- **Verification result**: per check — what was exercised, whether it passed, and on failure which
  component was at fault and where to look.
- **Known local gap**: a production behaviour the local environment cannot reproduce, with the reason and
  the substitute in use.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a clean machine, one command produces a system the developer can sign into and use in a
  browser, with no manual steps between the command and the working system.
- **SC-002**: A developer who has never run it before gets from repository to working system using only the
  command's own output — no tribal knowledge, no reading the source.
- **SC-003**: The command never reports success on a system that is not actually usable. Across repeated
  runs, including deliberately broken ones, false successes are **zero**.
- **SC-004**: A deliberately broken internal dependency is detected and named by verification rather than
  passing because the underlying resources exist.
- **SC-005**: 100% of deployable units are deployed from the same infrastructure definitions used for a real
  cloud; none is deployed by a local-only definition.
- **SC-006**: Teardown followed by a fresh run succeeds with zero manual cleanup and leaves no resource from
  the previous run behind.
- **SC-007**: Every local-only deviation from production is enumerated in one document; a reader can tell in
  under a minute what local success does and does not prove.
- **SC-008**: A warm re-run (images already built, no application changes) completes in under 5 minutes, so
  the command is practical to use repeatedly within a working session.
- **SC-009**: The deployment creates nothing in any real cloud account and requires no real credentials.

## Assumptions

- **Single client and environment per run.** The multi-client model already exists in the deployment
  simulation; this feature targets one environment at a time for simplicity and can be extended later.
- **The developer's machine can run the whole system.** Ten units plus a database and the emulator is a
  meaningful footprint; this is accepted as a developer-workstation feature.
- **Demo data and a usable sign-in come from the existing service seeders**, including at least one fully
  privileged account, since a system nobody can sign into cannot be manually tested.
- **A cold run's duration is dominated by building application images** and is therefore not tightly
  bounded; warm runs are the ones expected to be quick.
- **This feature does not deploy to a real cloud.** It makes the real-cloud path observable locally; the
  rollout itself remains future work.
- **The emulator's capabilities were established empirically** during research for this spec, including one
  capability gap with no equivalent through any of its documented interfaces. The substitute for that gap
  is a deployment-tooling concern and must not leak into application configuration.

## Dependencies

- The existing infrastructure module defining the real-cloud deployment, which this feature both consumes
  and corrects.
- The existing local cloud emulator harness introduced for the infrastructure gate.
- The application build definitions producing each deployable unit's image.
- The existing seeders that populate demo data and the privileged account used for manual testing.

## Out of Scope

- Deploying to a real cloud account, and anything requiring real credentials.
- Clouds other than the primary one; the other two emulators were assessed and set aside previously.
- Performance, load, or scale testing of the deployed system.
- Changing application behaviour. This feature changes how the system is deployed and observed, not what
  it does.
