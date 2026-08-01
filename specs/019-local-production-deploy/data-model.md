# Phase 1 Data Model: Local Production-Replica Deployment

This feature deploys software rather than storing business records, so its "data model" is the set of
declarations and results the deployment reasons about. Only the **Deployment environment** and the
**database** persist; everything else lives for the duration of a run.

---

## Deployable unit

One service or the user interface. Matches the existing `release_set` map entry in
`infra/terraform/modules/aws/main.tf` — **the shape is fixed by the real module and must not be forked.**

| Field | Type | Rules |
|---|---|---|
| `key` | string | Map key. Lowercase, hyphenated. Becomes the internal hostname and the ECS service name. |
| `image` | string | Repository reference. Locally the ECR URI; in production the real registry path. |
| `version` | string | Image tag. Together with `image` forms `image_ref` (`"${image}:${version}"`). |
| `visibility` | `public` \| `private` | `public` attaches the unit to the ALB. **Exactly one unit is public** (the frontend). |
| `port` | number | Container listening port. |
| `health_path` | string | Path polled until the unit is healthy. |

**Validation**

- Every key must have a buildable image definition; a key with no build is a configuration error.
- Exactly one `public` unit. Zero means the system is unreachable; more than one contradicts R7.
- `port` must be unique per unit and must match what the service actually binds.

**The nine units** (from R9): `frontend` (public); `edge`, `identity`, `gateway`, `operations`, `hr`,
`crm`, `procurement`, `workflow` (private). See [contracts/release-set.md](./contracts/release-set.md).

---

## Release Set

The versioned collection of deployable units deployed together, so the running system is a known consistent
combination rather than an accident of timing. Already an established concept in 001 — this feature reuses
it rather than inventing a parallel notion.

| Field | Type | Rules |
|---|---|---|
| `units` | map of Deployable unit | Non-empty. |
| `version` | string | One tag applied to every unit in a local run, so the set is unambiguous. |

**Rule**: a local run tags all units with the same version. Mixed versions are legal in production
(promoting one service) but would obscure what a local run actually proves.

---

## Deployment environment

Scopes every created resource and every internal name, keeping one run isolated from another (FR-003,
Constitution IV).

| Field | Type | Rules |
|---|---|---|
| `client_name` | string | Resource-name prefix. Defaults to a local-only value. |
| `env` | string | `stg` locally — never `prod`, so the module's production sizing path is never taken by accident. |
| `namespace` | derived | `{client_name}-{env}.internal` — the suffix of every internal hostname. |
| `entry_url` | derived | Host-reachable URL for the ALB; printed on success. |
| `emulator_endpoint` | string | Floci's API endpoint. Never a real cloud endpoint. |

**Invariant**: the resolved endpoint must be the local emulator. If real cloud credentials are present in
the environment, they are ignored and the run still targets the emulator (FR-003).

---

## Internal name binding

The local-only substitute for Cloud Map (R6). Exists **only** in the deployment tooling and never alters
deployed configuration (FR-009).

| Field | Type | Rules |
|---|---|---|
| `unit_key` | string | The deployable unit this binding serves. |
| `fqdn` | derived | `{unit_key}.{namespace}` — the name the module injects into peers. |
| `short_name` | string | The bare `{unit_key}`, required because `nginx.conf` proxies to `http://edge` (R7). |
| `container` | string | The placed container's generated name, resolved at bind time — never guessed. |

**Lifecycle**: created after ECS placement; **invalidated when a task is replaced**. A replacement leaves
peers unable to resolve the unit, so the deployment must either rebind or surface the failure — silent
degradation is the one outcome forbidden here.

---

## Verification result

One record per check, forming the evidence that the deployment is genuinely usable (FR-010 – FR-013).

| Field | Type | Rules |
|---|---|---|
| `check` | string | What was exercised, in user terms. |
| `kind` | `reachability` \| `health` \| `cross_service` \| `permission` | A run MUST include at least one of each of the last three. |
| `passed` | boolean | — |
| `component` | string | On failure, the unit at fault. |
| `evidence` | string | Observed status/response; on failure, the copy-pasteable log command (FR-014). |

**Rule**: `kind = health` alone can never constitute a passing run. Individual health with broken
inter-service calls is exactly the 017 failure mode (R8).

---

## Known local gap

A production behaviour the local environment cannot reproduce (FR-022). Enumerated in one place so nobody
mistakes local success for production readiness (SC-007).

| Field | Type | Rules |
|---|---|---|
| `behaviour` | string | What production does that local cannot. |
| `reason` | string | Why the emulator cannot reproduce it. |
| `substitute` | string | What is used locally instead, or "none". |
| `risk` | string | What could ship broken because of this gap. |

**Known gaps at plan time**

| Behaviour | Reason | Substitute | Risk |
|---|---|---|---|
| Cloud Map DNS resolution | Emulator implements the management API only (R6) | Docker network alias | None to production — production Cloud Map is untouched and unexercised locally |
| ECS capacity provisioning | Emulator places tasks without any capacity (R3) | none | **High** — production tasks stay `PENDING` if the capacity definitions are wrong; local success proves nothing here |
| TLS on the public entry | Local runs without a custom domain, so the listener is plain HTTP | none | Certificate and redirect behaviour unexercised |
