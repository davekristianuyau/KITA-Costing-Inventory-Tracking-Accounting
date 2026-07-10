# Feature Specification: Multi-Cloud CI/CD Infrastructure Scaffolding

**Feature Branch**: `001-multi-cloud-cicd`
**Created**: 2026-07-08
**Status**: Draft
**Input**: User description: "for our first spec this will be the scaffolding of CICD the infra its architecture, we will use terraform, i want the CICD to be able to deploy to AWS GCP Azure with minimal changes, if possible, a client can choose the cloud platform to deploy the application, the application will be like odoo or SAP Hana." + "also include that this will have a STG and PROD environment deployments" + "fold in the multi-service deployment (the application is a set of microservices + a web frontend behind a gateway, per feature 002-source-scaffold), not a single container"

## Overview

KITA is a business application (costing, inventory tracking, and accounting) in the
same class as Odoo or SAP HANA. Per feature `002-source-scaffold`, the application is
built as a **set of services**: a web frontend and multiple backend microservices
(Java/Spring Boot) fronted by a gateway, each packaged as its own container image. This
feature establishes the foundational infrastructure and delivery pipeline that provisions
and deploys that **multi-service application** onto a customer-selected public cloud (AWS,
Google Cloud, or Azure) with minimal, configuration-level changes between providers.

Each deployment provisions one isolated environment containing a private network, the set
of service workloads, a managed relational database, persistent storage, internal
service-to-service networking, and a single public gateway/ingress (TLS, custom domain).
Each deployment has two independent, isolated tiers — Staging (STG) and Production (PROD) —
where a coordinated set of service versions is proven in STG and then intentionally promoted
to PROD together. It is the scaffolding/delivery layer; it does not add business features to
the application itself.

## Clarifications

### Session 2026-07-08

- Q: How is the deployed application meant to be reached by its users? → A: Public
  internet-facing; access is governed by application-level username/password
  authentication, with support for serving under the client's own URL/custom domain.
- Q: How is the deployment region determined for a client? → A: A single default
  region is used for all clients unless a client explicitly requests a different
  region; a client's data stays within its chosen region. Near-term focus is local
  clients, so the single-default-region model is expected to cover most cases.
- Q: What is the recovery expectation for PROD financial data if the database is
  lost? → A: Daily automated backups plus point-in-time recovery (~minutes of
  potential data loss) on a single-node managed database.
- Q: When deploying a new version to PROD fails health checks, what should happen?
  → A: Auto-rollback — the previous healthy version keeps serving traffic and the
  failed version never receives traffic.
- Q: Should STG be sized the same as PROD? → A: No — STG is smaller than PROD by
  default; resource sizing is a per-environment configuration variable while both
  tiers keep the same architecture/shape.
- Decision (architecture amendment): The application is a set of microservices + a web
  frontend behind a gateway (feature 002), deployed as multiple container images per
  environment — not a single container. Only the gateway is publicly exposed; backend
  services run on the private network. Services are deployed and promoted together as a
  coordinated Release Set.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Deploy the multi-service application to a chosen cloud (Priority: P1)

An operator selects a single target cloud provider, supplies the required configuration
(including the set of service image versions to deploy), and triggers the delivery pipeline
against an environment. The pipeline provisions all required infrastructure (private
network, per-service compute, managed database, persistent storage, internal networking,
and a public gateway/ingress) and deploys every service so the application is reachable
through the gateway, services can reach the database and each other, and the whole set is
healthy.

**Why this priority**: This is the minimum viable capability. Without the ability to stand
up the full multi-service application on at least one cloud from a repeatable pipeline,
nothing else in the feature has value. It delivers a working, deployable product.

**Independent Test**: Run the pipeline against one provider and one environment with a known
set of service versions; verify the application's public gateway responds to a health check,
that the reference service round-trip works (frontend → gateway → service → database), and
that no manual provisioning steps were required.

**Acceptance Scenarios**:

1. **Given** a clean cloud account and a valid configuration for a target provider,
   **When** the operator triggers the pipeline, **Then** all required infrastructure is
   provisioned, every service is deployed, and the application reports healthy at its public
   gateway.
2. **Given** a completed deployment, **When** the operator re-runs the pipeline with the same
   configuration, **Then** no duplicate resources are created and the result is unchanged
   (idempotent provisioning).
3. **Given** a deployment in progress, **When** any provisioning or service-deployment step
   fails, **Then** the pipeline stops, reports the failing step and reason, and does not mark
   the deployment successful.

---

### User Story 2 - Separate STG and PROD with gated promotion (Priority: P1)

An operator maintains two independent, isolated environment tiers per deployment: Staging
(STG) for validating changes, and Production (PROD) for live use. A coordinated Release Set
of service versions is deployed to STG first; promotion to PROD is a deliberate, explicit
action that carries the same Release Set proven in STG. There is no automatic promotion from
STG to PROD.

**Why this priority**: For a financial/accounting system, changes must be provable in a safe
environment before they can affect real data. Distinct STG and PROD tiers with a deliberate
promotion gate are foundational to operating the product safely.

**Independent Test**: Deploy a Release Set to STG and confirm it is healthy and isolated;
promote the same Release Set to PROD and confirm PROD serves it; confirm STG and PROD do not
share a database or storage, and that no Release Set reaches PROD without passing STG.

**Acceptance Scenarios**:

1. **Given** a target provider and client, **When** the operator provisions deployments,
   **Then** STG and PROD exist as separate environments each with their own isolated network,
   database, storage, and service workloads — no shared data path between them.
2. **Given** a Release Set validated and healthy in STG, **When** the operator promotes it to
   PROD, **Then** PROD is deployed with the same set of service versions and equivalent
   configuration and reports healthy.
3. **Given** a Release Set that has not been deployed to STG, **When** a promotion to PROD is
   attempted, **Then** the pipeline blocks the promotion and reports that STG validation is
   required first.
4. **Given** a merge or pipeline trigger, **When** it completes for STG, **Then** PROD is NOT
   automatically updated — promotion requires an explicit, separate action.

---

### User Story 3 - Portability across clouds with minimal changes (Priority: P2)

An operator (on behalf of a client) chooses which of the supported clouds — AWS, Google
Cloud, or Azure — the application is deployed to. Switching the target provider requires only
configuration/parameter changes, not rewriting the delivery logic or the application.

**Why this priority**: Cloud choice is the differentiating capability of this feature. It
builds on Stories 1 and 2 and proves the architecture is genuinely provider-portable.

**Independent Test**: Take a configuration that deploys the service set successfully to one
provider, change only the provider selection and provider-specific settings, and run the
pipeline against a second provider; verify the application reaches the same healthy,
functional state without changes to pipeline or application logic.

**Acceptance Scenarios**:

1. **Given** a working deployment on one provider, **When** the operator changes only the
   provider selection and provider-specific configuration, **Then** the pipeline deploys an
   equivalent, healthy multi-service stack on the newly selected provider.
2. **Given** an unsupported or misspelled provider value, **When** the operator triggers the
   pipeline, **Then** the pipeline rejects it with a clear message listing the supported
   providers before provisioning anything.
3. **Given** deployments on two different providers, **When** an end user exercises the
   application, **Then** behavior is functionally equivalent regardless of the underlying
   provider.

---

### User Story 4 - Isolated per-client environments (Priority: P3)

An operator provisions a dedicated, isolated set of environments for a specific client so
that each client's application, data, and infrastructure (across both STG and PROD) are
separated from every other client's.

**Why this priority**: Delivering the product to multiple clients requires that one client's
data and workloads never mix with another's. Important for a financial system but depends on
Stories 1–3 being in place first.

**Independent Test**: Provision two named client deployments and confirm each has its own
isolated STG and PROD tiers — separate network boundary, database, storage, and service
workloads — with no shared data path between clients.

**Acceptance Scenarios**:

1. **Given** a client identifier and target provider, **When** the operator provisions a
   client deployment, **Then** the client's STG and PROD environments have their own isolated
   network, database, storage, and services, tagged/named to that client.
2. **Given** two provisioned client deployments, **When** one is redeployed or torn down,
   **Then** the other is unaffected.

---

### User Story 5 - Lifecycle management (Priority: P3)

An operator updates a running deployment to a new Release Set and can tear down an
environment completely when it is no longer needed, reclaiming all resources.

**Why this priority**: A deployment that cannot be safely updated or removed accumulates cost
and risk. Lifecycle control is necessary for real operation but follows the ability to create
deployments.

**Independent Test**: Deploy a Release Set to an environment, update to a newer Release Set
and confirm the new versions serve traffic; then tear the environment down and confirm no
billable resources from that environment remain.

**Acceptance Scenarios**:

1. **Given** a running deployment, **When** the operator deploys a new Release Set, **Then**
   the new service versions become active and the deployment reports healthy.
2. **Given** a provisioned environment, **When** the operator runs teardown, **Then** all
   resources created for that environment (all services, database, storage, network, gateway)
   are removed and confirmed gone.

---

### User Story 6 - Multi-service topology, gateway, and networking (Priority: P1)

An operator deploys the set of services so that a single public gateway/ingress fronts them,
each backend microservice runs privately (no public exposure), services can reach one another
and the shared database over the private network, and the deployment is reported healthy only
when all required services are healthy.

**Why this priority**: The multi-service shape is the defining change folded into this
feature. Deploying a set of networked services behind one gateway — rather than a single
container — is a prerequisite for US1 to mean anything now, so it is P1 alongside the core
deploy.

**Independent Test**: On a deployed environment, confirm only the gateway is reachable from
the public internet, a backend service is NOT directly reachable publicly, the gateway routes
a request to the reference service and back, and stopping one required service turns the
aggregate health indicator unhealthy.

**Acceptance Scenarios**:

1. **Given** a deployed environment, **When** the public gateway URL is called, **Then** it
   routes to the correct backend service and returns the expected response.
2. **Given** a deployed environment, **When** a backend microservice endpoint is probed
   directly from the public internet, **Then** it is not reachable (private only).
3. **Given** all required services running, **When** aggregate health is checked, **Then** it
   reports healthy; **When** a required service is stopped, **Then** aggregate health reports
   unhealthy and the deployment is not considered successful.
4. **Given** the service set, **When** the gateway or a service needs to reach another service
   or the database, **Then** it does so over the private network, not the public internet.

---

### Edge Cases

- What happens when cloud credentials are missing, expired, or lack sufficient permissions?
  The pipeline must fail fast with a clear message and provision nothing.
- What happens when a provisioning run is interrupted midway? A subsequent run must reconcile
  to the desired state without leaving orphaned or duplicated resources.
- How does the system handle a partial failure where some services deployed but others failed?
  The deployment must not be reported successful; recovery is roll back or safe re-run to a
  fully healthy set.
- What happens when the service versions in a Release Set are mutually incompatible? The
  pipeline must treat the Release Set as the unit and surface the problem rather than deploying
  a mismatched set.
- What happens when someone attempts to promote a Release Set to PROD that never passed STG,
  or that failed in STG? The promotion must be blocked.
- What happens when a requested provider or region does not offer a required capability? The
  pipeline must surface the limitation before committing changes.
- How are secrets handled so they never appear in the repository, logs, or pipeline output,
  and are kept distinct per environment and available to each service that needs them?
- What happens when two operators trigger changes to the same environment concurrently?
  Concurrent state changes must be prevented or serialized.
- What happens when the gateway is healthy but a downstream service is not? The gateway must
  return a clear error for that route while the deployment's aggregate health reflects the
  unhealthy service.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provision the complete infrastructure required to run the
  multi-service application on a selected cloud provider, including a private network,
  per-service compute for every service in the set, a managed relational database, persistent
  storage, internal service-to-service networking, and a single public gateway/ingress served
  over TLS.
- **FR-001a**: Access to the running application MUST be governed by application-level
  username/password authentication at the gateway/application boundary; the infrastructure
  MUST expose the application publicly (via the gateway) but MUST NOT itself grant access
  beyond reaching the login boundary.
- **FR-001b**: The system MUST support serving each client's environment under the client's
  own URL/custom domain, with valid TLS for that domain, at the public gateway.
- **FR-001c**: Only the gateway/ingress MUST be publicly reachable; backend microservices MUST
  run on the private network with no public ingress.
- **FR-002**: The system MUST support AWS, Google Cloud, and Azure as selectable deployment
  targets.
- **FR-002a**: The system MUST deploy to a single default region for all clients unless a
  client explicitly requests a different region, in which case region MUST be overridable per
  client via configuration. A client's data MUST remain within its chosen region.
- **FR-003**: An operator MUST be able to select the target cloud provider through
  configuration, and switching providers MUST require only configuration changes — not changes
  to the delivery pipeline logic or the application code.
- **FR-004**: The system MUST deploy all services in the Release Set after infrastructure is
  provisioned and MUST verify the whole set is healthy (aggregate health) before reporting
  success.
- **FR-004a**: The gateway MUST route external requests to the correct backend service and
  serve as the single public entry point; service-to-service and service-to-database traffic
  MUST traverse the private network only.
- **FR-005**: Provisioning MUST be idempotent and reproducible — re-running with the same
  configuration produces the same result without duplicating resources.
- **FR-006**: The pipeline MUST fail fast: if any provisioning, service-deployment, or quality
  step fails, it MUST stop, report the failing step and reason, and MUST NOT report a
  successful deployment.
- **FR-006a**: When a new Release Set (or any service within it) fails health checks during
  deployment, the system MUST automatically roll back so the previous healthy Release Set
  continues serving traffic; the failed versions MUST NOT receive user traffic.
- **FR-007**: The system MUST validate the selected provider and required configuration
  (including the set of service images/versions) before provisioning, rejecting unsupported or
  incomplete input with a clear message.
- **FR-008**: The system MUST provide two distinct environment tiers — STG and PROD — for each
  deployment, each fully isolated with its own network, database, storage, and service
  workloads, and with no shared data path between them.
- **FR-008a**: STG and PROD MUST share the same architecture/shape (same set of services), but
  resource sizing MUST be a per-environment configuration variable so STG can run smaller than
  PROD by default; changing sizing MUST NOT require changes to pipeline or application logic.
- **FR-009**: A Release Set MUST be deployable to STG independently of PROD, and PROD MUST NOT
  be updated automatically as a side effect of a STG deployment or a code merge.
- **FR-010**: Promotion from STG to PROD MUST be an explicit, deliberate action that carries
  the same Release Set (identical service versions) and equivalent configuration validated in
  STG.
- **FR-011**: The system MUST block promotion to PROD of any Release Set that has not been
  successfully deployed and verified healthy in STG.
- **FR-012**: The system MUST support provisioning isolated, per-client deployments where each
  client's STG and PROD environments (all services, data, and networks) are separated from
  other clients'.
- **FR-013**: Secrets and credentials MUST NOT be stored in the repository, MUST NOT appear in
  logs or pipeline output, MUST be scoped per environment (STG distinct from PROD), and MUST be
  delivered to each service that requires them at runtime.
- **FR-014**: Data managed by the application MUST be encrypted at rest and in transit wherever
  the target provider supports it.
- **FR-014a**: The PROD managed database MUST have daily automated backups and point-in-time
  recovery enabled (recovery point within minutes), and backups MUST be proven restorable
  before a PROD environment is used. STG backup requirements MAY be relaxed relative to PROD.
- **FR-015**: The system MUST allow updating a running deployment to a new Release Set and MUST
  support tearing down an environment so that all of its resources (all services, database,
  storage, network, gateway) are removed.
- **FR-016**: The system MUST record an auditable history of deployment and promotion activity
  (which Release Set was deployed or promoted, to which provider/environment, when, and the
  outcome).
- **FR-017**: The system MUST prevent conflicting concurrent changes to the same environment's
  infrastructure state.
- **FR-018**: The system MUST emit structured logs per service and expose an aggregate
  application health indicator sufficient to diagnose deployment and runtime failures without
  manual reproduction, per environment.
- **FR-019**: Infrastructure definitions and pipeline configuration MUST live in the repository
  under version control so that any environment can be reproduced from a known revision.
- **FR-020**: Every provisioned environment and its resources MUST follow a consistent naming
  convention derived from configuration variables in the form `{client-name}-{env}` (e.g.,
  `acme-stg`, `acme-prod`), where `{env}` is one of `stg` or `prod`, with per-service
  resources further identified by service name (e.g., `acme-prod-gateway`). Names MUST be
  derived from variables — never hard-coded.
- **FR-021**: Resource names and tags MUST make the owning client, environment tier, and
  service unambiguously identifiable, so resources belonging to different clients, tiers, or
  services are never confused during operation, audit, or teardown.
- **FR-022**: The system MUST treat the set of service image versions as a single coordinated
  Release Set — deployed, promoted, rolled back, and audited together — so a running
  environment never mixes incompatible service versions.

### Key Entities *(include if feature involves data)*

- **Deployment Target**: A selected cloud provider (AWS, Google Cloud, or Azure) plus the
  region and provider-specific settings that direct where and how infrastructure is created.
- **Environment**: A named, isolated instance of the full multi-service stack at a specific
  tier — STG or PROD. Owns its own network, database, storage, service workloads, and gateway.
- **Environment Tier**: The role an environment plays — STG (validation) or PROD (live) —
  which governs promotion order and gating. Expressed as the `{env}` token in resource names.
- **Naming Convention**: A variable-driven rule naming every environment/resource as
  `{client-name}-{env}` (with per-service suffixes), ensuring collision-free identification.
- **Client**: A customer for whom a dedicated, isolated set of environments (STG and PROD) is
  provisioned; identified by a stable name/identifier used for tagging and isolation.
- **Application (Service Set)**: The running application as a set of Services (web frontend,
  gateway, and backend microservices) plus shared dependent resources (managed database,
  storage) within an environment.
- **Service**: One deployable workload within the environment — the frontend, the gateway, or
  a backend microservice — with its own container image, private/networked placement, and
  health endpoint.
- **Gateway / Ingress**: The single publicly reachable entry point that terminates TLS, serves
  the client's custom domain, enforces the app auth boundary, and routes to backend services.
- **Release Set**: The coordinated set of service image versions known to be compatible,
  deployed/promoted/rolled back and audited together (the unit of deployment and promotion).
- **Promotion**: The deliberate act of moving a validated Release Set and its configuration
  from STG to PROD.
- **Deployment Record**: An auditable entry describing a deployment or promotion attempt —
  target, environment, tier, Release Set, timestamp, and outcome.
- **Configuration/Secrets Set**: The environment- and provider-scoped values (settings and
  sensitive credentials) that parameterize a deployment without changing pipeline or
  application logic; scoped separately for STG and PROD and delivered per service.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can take a clean cloud account to a fully deployed, healthy
  multi-service application on any one supported provider by triggering a single pipeline run
  with no manual provisioning steps.
- **SC-002**: Moving a working deployment from one supported cloud to another requires changing
  only configuration values, with zero changes to pipeline or application logic, and reaches an
  equivalent healthy state.
- **SC-003**: Re-running provisioning with an unchanged configuration results in no resource
  changes 100% of the time (provably idempotent).
- **SC-004**: Every deployment either completes successfully with all services healthy or fails
  with a clear, logged reason — there are no silent partial successes.
- **SC-005**: STG and PROD for the same deployment share no network path, database, storage, or
  service workloads, verified by inspection.
- **SC-006**: 100% of PROD deployments trace back to a Release Set previously deployed and
  verified in STG; no Release Set reaches PROD without passing STG.
- **SC-007**: No STG deployment or code merge ever changes PROD without a separate, explicit
  promotion action.
- **SC-008**: Two client deployments provisioned on the same provider share no network path,
  database, storage, or services across either tier, verified by inspection.
- **SC-009**: A provisioned environment can be torn down so that 100% of its billable resources
  (all services, database, storage, network, gateway) are removed, confirmed by inspection.
- **SC-010**: No secret value ever appears in the repository history, pipeline logs, or pipeline
  output.
- **SC-011**: Any environment can be reproduced from a specific repository revision without
  relying on undocumented manual steps.
- **SC-012**: 100% of provisioned resources follow the `{client-name}-{env}` naming convention
  (with per-service identification), verified by inspection, with no hard-coded or ad-hoc names.
- **SC-013**: Only the gateway is reachable from the public internet; 0% of backend
  microservices are directly reachable publicly, verified by inspection.
- **SC-014**: A deployment is reported healthy only when all required services are healthy; if
  any required service is unhealthy, aggregate health reports unhealthy 100% of the time.

## Assumptions

- **Application shape (amended)**: The application is a set of services — a web frontend and
  multiple Java/Spring Boot microservices behind a gateway (feature `002-source-scaffold`) —
  each with its own container image. This supersedes the earlier single-container assumption.
- **Public surface**: Only the gateway/ingress is public (serving the frontend and routing to
  services under the client's custom domain, over TLS); backend microservices are private.
- **Shared data store per environment**: Each environment uses one managed PostgreSQL instance
  shared by the services with logical separation (e.g., schema per service), rather than a
  separate database per service, to limit cost and operational load for a solo maintainer.
  Persistent/object storage is likewise shared per environment.
- **Release Set**: Deployment/promotion operates on a coordinated set of service versions; the
  same image versions validated in STG are promoted to PROD (version-based, no rebuild).
- **Environment tiers**: Exactly two managed cloud tiers are in scope — STG and PROD. Local
  developer environments are out of scope for this cloud pipeline (they belong to feature 002).
- **Naming convention**: Resources are named `{client-name}-{env}` (with per-service suffixes)
  where `{env}` is `stg` or `prod`; `{client-name}` is a stable, lowercase, provider-safe id.
  Providers with stricter naming rules adapt minimally while preserving the identifiers.
- **Infrastructure-as-code tool**: Terraform (explicitly chosen). A single Terraform codebase
  with provider-selectable modules, parameterized per environment tier and iterating over the
  service set, is assumed over separate codebases, to satisfy the "minimal changes" requirement.
- **Tenancy**: Single-tenant per client — each client receives its own isolated STG and PROD
  environments rather than sharing a multi-tenant instance.
- **Cloud accounts**: The operator supplies valid credentials and accounts for the target
  provider(s); account creation and billing setup are out of scope.
- **Managed services**: Where a cloud offers an equivalent managed service (managed relational
  database, container runtime, load balancer/ingress, private networking), the managed service
  is preferred over self-hosting it.
- **Operator role**: In this solo-developer context, "operator" is the project owner acting on
  behalf of a client; there is no multi-role access-control system in scope.

## Dependencies

- Feature `002-source-scaffold` defines the set of services, their images, per-service health
  endpoints, gateway, and the Release Set concept this feature deploys.
- Valid cloud provider accounts and credentials for any provider being targeted, scoped per
  environment tier where required.
- A CI/CD execution environment capable of running the pipeline and holding secrets securely
  per environment.
- A registry/artifact source holding all service images referenced by a Release Set.
- Remote state storage so infrastructure state is durable and lockable, isolated per
  environment.

## Out of Scope

- Business/functional features of the KITA application itself (costing, inventory, accounting
  logic) and the service source code (owned by feature 002).
- On-premises or private-cloud deployment targets beyond AWS, Google Cloud, and Azure.
- A customer-facing self-service UI for choosing a cloud; selection is configuration-driven.
- Multi-tenant shared-instance architecture.
- Local/developer environment provisioning through this cloud pipeline.
- Cloud account provisioning, billing, and organization/landing-zone setup.
- A separate database per microservice (a shared per-environment database is assumed).
