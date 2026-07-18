# Feature Specification: Containerized Database & Cache Runtime

**Feature Branch**: `008-docker-cache-database`
**Created**: 2026-07-18
**Status**: Draft
**Input**: User description: "now we create actual backend cache and database image that runs in docker"

## Summary

Today the backend runs against in-memory fakes and per-test throwaway datastores; there is no
reproducible way to stand up the real data tier. This feature delivers **real, containerized database
and cache services** that the backend runs against, and a **one-command way to bring up the full
backend** (API gateway + all microservices + database + cache) on an isolated container network. The
containerized database and cache are **production-representative**: the same pinned engine versions,
schema migrations, and configuration mechanism used locally are the ones used in production, so what a
developer tests locally behaves the same as what ships. The cache is introduced and applied **only where
a concrete need exists**, never as blanket caching.

## Clarifications

### Session 2026-07-18

- Q: How should the containerized database host the services' data? → A: A single database server with **one shared database, one schema per service**; each service owns and writes only its own schema (migrations target that schema, not `public`), and cross-service data needs are served through the owning service's API rather than cross-schema writes. Read-only cross-schema access within the single database is physically possible but not the default.
- Q: Which hot read path should the cache serve? → A: The cache is **shared infrastructure available to all services**; each service caches its own identified hot read paths where a concrete need exists (at least one path implemented and tested to prove the tier). Blanket/whole-application caching stays out of scope.
- Q: Should the full-stack bring-up include the frontend? → A: **Backend-only** (gateway + all backend services + database + cache). The frontend has not been specced/created yet and will be added in a later spec; the API gateway is the sole external entry point for now.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Real database & cache the backend runs against (Priority: P1)

A developer starts a database service and a cache service as containers and points a backend service at
them. The service connects, applies its schema migrations automatically, reads and writes real data, and
the data survives a container restart. This replaces "only fakes / per-test containers" with a durable,
inspectable data tier.

**Why this priority**: This is the core of the request — an *actual* database and cache running in
containers that the backend uses. Everything else builds on it, and it delivers value on its own: a
developer can finally run a service against real persistence and a real cache.

**Independent Test**: Bring up only the database and cache containers, start one backend service against
them, create a record, restart the containers, and confirm the record is still present and the service
reconnects. Confirm the cache is reachable and reports healthy.

**Acceptance Scenarios**:

1. **Given** no data tier is running, **When** the developer runs the documented start command, **Then**
   a healthy database service and a healthy cache service are available within the documented startup
   budget.
2. **Given** the database container is fresh, **When** a backend service starts against it, **Then** that
   service's schema migrations are applied automatically and the service reports healthy.
3. **Given** data has been written, **When** the containers are stopped and started again, **Then** the
   previously written data is still present (no data loss across restarts).
4. **Given** the cache service is stopped while a service is running, **When** a request that would use
   the cache arrives, **Then** the service still returns correct results from the database (the cache is
   an accelerator, never the source of truth).

---

### User Story 2 - One-command full backend stack (Priority: P2)

A developer brings up the entire backend — API gateway, every backend microservice, the database, and
the cache — with a single command, on an isolated network where only the gateway is reachable from
outside. They then exercise the system end-to-end through the gateway. (The frontend is not part of this
stack yet — it will be added in a later spec — so the gateway is the sole external entry point for now.)

**Why this priority**: The user asked for the full backend stack. A single reproducible command that
yields a complete, correctly networked environment is what makes the data tier usable for real
end-to-end work and demos, and removes manual, error-prone local setup.

**Independent Test**: From a clean checkout, run the single start command, wait for healthy, then call a
representative endpoint through the gateway and get a correct end-to-end response that touches the
database. Confirm the database and cache are **not** reachable directly from outside the container
network.

**Acceptance Scenarios**:

1. **Given** a clean machine with the container runtime installed, **When** the developer runs the single
   start command, **Then** the gateway, all backend services, the database, and the cache all reach a
   healthy state, and services do not accept traffic until their dependencies are healthy.
2. **Given** the full stack is up, **When** a request is sent through the gateway, **Then** it is routed
   to the correct service and returns a correct response backed by the containerized database.
3. **Given** the full stack is up, **When** an external client attempts to reach the database, cache, or
   a private backend service directly, **Then** the connection is refused (only the gateway is exposed).
4. **Given** the stack is running, **When** the developer runs the single stop/teardown command, **Then**
   all containers stop and, on request, associated data volumes are removed cleanly.

---

### User Story 3 - Local behaves like production (parity) (Priority: P2)

Because the containerized database and cache are meant to be the same as production, a developer can
trust that behavior observed locally matches production: the same engine versions, the same migrations,
and the same configuration mechanism apply in both places, differing only by environment-scoped values
(hostnames, credentials, sizing).

**Why this priority**: Parity is the stated intent ("should be the same for the one in production").
Without it the local stack is just a convenience; with it, local testing becomes a trustworthy proxy for
production and prevents "works locally, breaks in prod" defects in a financial system.

**Independent Test**: Compare the pinned engine versions and applied migration set between the local
configuration and the production configuration and confirm they match (same major/minor engine version,
same migration history), with only environment-scoped values differing.

**Acceptance Scenarios**:

1. **Given** the local and production configurations, **When** they are compared, **Then** the database
   and cache engine versions are pinned to the same major/minor in both, and no version drift is allowed.
2. **Given** a schema migration is added, **When** it is applied, **Then** it applies identically to the
   local containerized database and to production through the same repeatable mechanism.
3. **Given** environment-specific values (credentials, hostnames, sizing), **When** switching between
   local and production, **Then** only those environment-scoped values change — no code or engine change
   is required.
4. **Given** the production database, **When** it is used to hold financial data, **Then** a restorable
   backup of that data exists before it is relied upon.

---

### User Story 4 - Shared cache, used by services where actually needed (Priority: P3)

The cache is shared infrastructure available to every backend service. Each service caches its own
identified hot read paths (frequently read, rarely changed reference/read data) where a concrete need
exists — serving reads from the cache and invalidating or refreshing the cached entry when the underlying
data changes, so reads are faster and repeat load on the database drops, without ever returning stale
data after a write. At least one such path is implemented and tested to prove the tier end-to-end.

**Why this priority**: The user asked to "use the cache where it is only needed," available to "all
services that need the cache." This proves the shared cache tier end-to-end and delivers a measurable
performance benefit on real paths, while deliberately avoiding premature/blanket caching (Simplicity &
YAGNI) — each service opts in only where it has a genuine need.

**Independent Test**: For a cache-eligible resource in a service, read it twice and confirm the second
read is served from the cache; then change that resource and confirm the next read reflects the change
(no stale value); confirm the database is queried fewer times for repeated reads.

**Acceptance Scenarios**:

1. **Given** a cache-eligible resource has been read once, **When** it is read again before it changes,
   **Then** the result is served from the cache without re-querying the database.
2. **Given** a cached resource, **When** the underlying data is changed through the system, **Then** the
   cache entry is invalidated or refreshed so the next read returns the new value.
3. **Given** the cache is empty or unavailable, **When** the resource is requested, **Then** the correct
   value is still returned from the database (graceful degradation).

---

### Edge Cases

- **Port conflicts**: a required host port is already in use → startup fails fast with a clear,
  actionable message rather than binding silently to the wrong thing.
- **Incompatible existing volume**: a persisted data volume was created by an older/newer schema or
  engine version → the mismatch is detected and reported rather than corrupting data.
- **Startup ordering**: a service starts before the database/cache is ready → the service waits for
  dependency health and does not report healthy or accept traffic prematurely.
- **Missing secret/credential**: a required credential is not provided → the affected container fails
  fast with a clear message and never falls back to an insecure built-in default outside local dev.
- **Cache/DB divergence after a write**: a write succeeds but cache invalidation is required → the next
  read must not return a stale value.
- **Datastore crash mid-run**: the database or cache restarts while services run → services reconnect
  automatically once the dependency is healthy again.
- **Teardown semantics**: distinguish "stop containers but keep data" from "remove data volumes" so a
  developer does not lose data unintentionally, and can also get a truly clean slate on demand.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a containerized database service that backend microservices connect
  to for durable storage.
- **FR-002**: The system MUST provide a containerized cache service that backend microservices can
  connect to as an accelerator.
- **FR-003**: The database and cache services MUST be startable with a single documented command and
  reach a **healthy** state that dependent services can gate on.
- **FR-004**: Database data MUST persist across container restarts and recreation via durable volumes;
  stopping and starting the data tier MUST NOT lose committed data.
- **FR-005**: Each backend service's schema migrations MUST be applied to the containerized database
  automatically through the existing repeatable, versioned migration mechanism — never by manual edits.
- **FR-006**: The system MUST provide a single command that brings up the **full backend stack** — API
  gateway, all backend microservices, the database, and the cache — as one orchestrated environment.
- **FR-007**: Services in the stack MUST NOT accept traffic until the datastores they depend on are
  healthy (dependency-ordered, health-gated startup).
- **FR-008**: Only the API gateway MUST be reachable from outside the container network; the database,
  the cache, and the private backend services MUST NOT be publicly exposed. (The frontend is not part of
  this stack yet; when it is added later it becomes the other externally exposed entry point.)
- **FR-009**: The system MUST provide a documented teardown that stops the stack, with an explicit,
  separate option to also remove data volumes for a clean slate.
- **FR-010**: All connection details and credentials MUST be supplied via environment-scoped
  configuration or a secrets store — never hard-coded in images, orchestration definitions, or the repo.
- **FR-011**: Database and cache engine versions MUST be pinned to explicit versions for reproducible
  builds, and MUST be the same major/minor version locally and in production (no version drift).
- **FR-012**: The configuration mechanism MUST be identical across environments, with only
  environment-scoped values (hostnames, credentials, sizing) differing between local and production.
- **FR-013**: The cache MUST be shared infrastructure reachable by every backend service, used only for
  each service's identified read paths that concretely benefit; the system MUST NOT depend on the cache
  as a source of truth, and MUST return correct results from the database when the cache is unavailable.
  At least one cached read path MUST be implemented and tested in this feature.
- **FR-014**: When cached data's underlying source changes, the system MUST invalidate or refresh the
  affected cache entries so reads never return a value known to be stale after a completed write.
- **FR-015**: Each long-running containerized service (datastores, gateway, backend services) MUST expose
  a health/status check.
- **FR-016**: Startup and connection failures (missing secret, unreachable dependency, port conflict,
  incompatible volume) MUST produce structured, actionable log output rather than silent failure.
- **FR-017**: For production use of the containerized database holding financial data, a restorable
  backup MUST exist before the database is relied upon.
- **FR-018**: Development, staging, and production datastores MUST remain separate; a single data tier
  MUST NOT be shared across environments.
- **FR-019**: Connections to the datastores MUST use encryption in transit where the environment supports
  it (parity target: enabled in production).
- **FR-020**: The data tier MUST run a single database server hosting one shared database with **one
  schema per backend service**; each service's versioned migrations MUST target its own schema (not the
  shared `public` schema), and each service MUST write only its own schema.
- **FR-021**: Cross-service data needs MUST be satisfied through the owning service's API, not by one
  service writing another service's schema. Read-only cross-schema access within the single database MAY
  be used only where a concrete need is justified.

### Key Entities *(include if feature involves data)*

- **Database service**: the single containerized, durable store that holds every backend service's data;
  one shared database with one schema per service (each service owns/writes its own schema);
  production-representative (pinned engine + version), persisted on a volume.
- **Cache service**: the single containerized, fast key-based store shared by all backend services, used
  only for each service's identified hot read paths; holds derived/copied data, never authoritative
  state.
- **Stack orchestration definition**: the declaration that brings up the gateway, all backend services,
  the database, and the cache together with correct networking, health gating, and startup order.
- **Environment configuration**: the environment-scoped set of connection endpoints, credentials, and
  sizing values that differ per environment while the mechanism stays identical.
- **Data volume**: the durable storage backing the database (and any persistent cache), enabling
  persistence across restarts and explicit removal on teardown.
- **Health probe**: the per-service readiness/liveness signal other components gate on.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From a clean checkout on a machine with only the container runtime installed, a developer
  can bring up the full healthy backend stack with a single command in under 10 minutes, with zero manual
  database or cache setup steps.
- **SC-002**: Committed database data survives a full stop/start of the data tier with 0% data loss.
- **SC-003**: A representative request through the gateway returns a correct end-to-end response backed by
  the containerized database on a freshly started stack.
- **SC-004**: The database and cache engine versions match (same major/minor) between the local and
  production configurations — verifiable and enforced, with 0 version-drift exceptions.
- **SC-005**: The database, cache, and private backend services are not reachable from outside the
  container network in 100% of connection attempts; only the API gateway accepts external traffic.
- **SC-006**: For each cached read path, repeated reads of unchanged data query the database at least 80%
  less often than without the cache, and a write is reflected on the next read 100% of the time (no stale
  reads).
- **SC-007**: With the cache service stopped, 100% of cache-eligible reads still return correct results
  from the database.
- **SC-008**: A developer can move the whole environment between local and production by changing only
  environment-scoped values — no code, engine, or migration change required.

## Assumptions

- **Data tier engines**: a relational database (matching the existing managed-PostgreSQL architecture)
  and an in-memory key/value cache are assumed; the concrete engines and versions are pinned in the plan.
- **Production topology & parity**: "same as production" is interpreted as *behavioral parity* — the
  containerized image defines the canonical engine, version, migrations, and configuration mechanism;
  production runs either the same image or a managed instance pinned to the identical engine major/minor
  with the same migrations and configuration. It does not mandate replacing managed cloud databases with
  self-hosted containers in production.
- **Migrations already exist**: backend services already own versioned schema migrations applied through
  a repeatable mechanism; this feature wires those to run against the containerized database and moves
  each service's migrations from the shared `public` schema into its own per-service schema — not new
  migration tooling.
- **Cache target paths**: the cache is shared across services; each service caches its own genuine hot
  read paths where a concrete need exists, with at least one path implemented and tested in this feature.
  Broader/blanket caching is deferred (YAGNI).
- **Frontend deferred**: the frontend has not yet been specced or created; it is intentionally excluded
  from this stack and will be introduced in a later spec, after which it becomes a second exposed entry
  point.
- **Single-developer, local-first**: the primary user is the solo developer running the stack locally and
  in CI; the same artifacts inform production configuration.

## Dependencies

- A container runtime and multi-container orchestration capability on the developer/CI machine.
- Existing backend microservices, the API gateway, and their per-service schema migrations.
- An environment-scoped secrets/configuration source for credentials (no secrets in the repository).

## Out of Scope

- Blanket/whole-application caching or caching read paths beyond the one identified need.
- Replacing managed cloud databases with self-hosted containers in production.
- High-availability/replication, automated backup scheduling, and disaster-recovery orchestration for
  production (beyond requiring that a restorable backup exists before production reliance).
- Provisioning cloud infrastructure (covered by the multi-cloud CI/CD infrastructure work).
- The frontend entirely — it has not been specced/created yet and will be handled in a later spec; this
  feature keeps the API gateway as the single external entry point.
