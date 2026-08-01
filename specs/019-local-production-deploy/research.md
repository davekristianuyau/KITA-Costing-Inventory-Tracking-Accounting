# Phase 0 Research: Local Production-Replica Deployment

All findings below were produced by running commands against a live Floci emulator on **2026-08-01**.
Nothing here is inferred from documentation — twice during this session a documentation-shaped assumption
turned out to be wrong, once in each direction. **Do not re-derive these; do re-verify before trusting them
against a new Floci version.**

---

## R1. Floci runs an undocumented DNS server on UDP 53 — this is the linchpin

**Decision**: every container that must resolve a Floci-managed name is started with Floci as its resolver
(`--dns <floci-container-ip>`).

**Rationale**: enumerating listeners inside the Floci container shows `UDP 53` bound. Nothing on floci.io or
in the repository README mentions a DNS server. A container using Docker's default resolver (`127.0.0.11`)
receives NXDOMAIN for every Floci-generated name.

**This single fact inverted a conclusion.** An earlier test declared "the ALB is API-only, it does not serve
traffic" — the test had simply never asked Floci's resolver. Re-running it correctly showed the ALB working
completely. Any future "Floci doesn't support X" conclusion must first confirm the resolver was used.

**Alternatives considered**: publishing container ports and addressing everything by `localhost` — rejected,
it abandons the production path that justifies the feature.

---

## R2. The ALB fully works — the browser entry point is production-identical

**Decision**: browser traffic enters through the ALB. Floci's listener port is published from the Floci
container to the host.

**Rationale**: with the resolver correct, `<name>-<id>.elb.<FLOCI_HOSTNAME>` resolves to Floci's IP and real
HTTP is proxied to the registered ECS target. Creating a listener on `:80` causes Floci to **bind `:80`
inside its own container** (confirmed by comparing listeners before and after), so `-p 8080:80` on the Floci
container lets the host browser reach the app *through the load balancer* — verified `HTTP 200` from Windows.

**Consequence**: the `hostPort` publishing scheme considered earlier is unnecessary. Requests follow
browser → ALB → target group → task, which is what production will do.

**Alternatives considered**: a host-file entry mapping the ALB DNS name to `127.0.0.1` so the browser uses the
production URL — cosmetic, needs admin rights, rejected.

---

## R3. ECS runs EC2-launch-type *services* as real containers

**Decision**: switch the module to the EC2 launch type (the owner's cost decision, and already the roadmap's
intent — ECS-on-EC2-Graviton, explicitly not Fargate).

**Rationale**: an ECS **service** with `desired_count=1`, `requiresCompatibilities: [EC2]`,
`networkMode: bridge` was placed by Floci as a real Docker container with its port published, serving
`HTTP 200`. Prior notes only covered `run-task`, which is a different code path.

**⚠️ The asymmetry that matters**: Floci places EC2-launch-type tasks **without any capacity** — no ASG, no
container instances, no capacity provider. **Real AWS does not.** A module that flips the launch type and
stops there deploys perfectly locally and leaves tasks `PENDING` forever in AWS. Per the clarification
session, the capacity infrastructure is therefore built in this feature and recorded as
**not verifiable locally** (FR-021, FR-022).

---

## R4. ECR works end to end — no registry credentials needed

**Decision**: build images, create ECR repositories idempotently, tag with the ECR URI, push, and let ECS
pull from ECR — the production distribution path.

**Rationale**: `create-repository` returns a URI on **port 5100**; `docker push` succeeds with **no
`docker login`** over plain HTTP; ECS then pulls that image and runs it (`HTTP 200`).

**Two operational traps, both confirmed:**

1. Floci spawns its own `registry:2` sibling container that **binds 5100 itself**. Publishing 5100 from the
   Floci container fails with "port is already allocated". Publish only `4566` and the ALB port.
2. Restarting Floci **wipes the ECR API state** (`RepositoryNotFoundException`) while the registry sibling
   keeps the blobs. Repository creation must therefore be idempotent and re-run on every start (FR-016,
   and the "already exists / unexpectedly missing" edge case).

**Alternatives considered**: skipping the registry, since locally-built images also run — rejected, it
diverges from how production distributes builds (FR-004).

---

## R5. RDS provisions a real PostgreSQL, immediately

**Decision**: deploy with `emulated = false` so the module's real `aws_db_instance` is created.

**Rationale**: `create-db-instance --engine postgres --engine-version 16.3` returned status `available`
**immediately** and spawned `postgres:16.3-alpine`; `psql` confirmed `PostgreSQL 16.3` with the master
credentials and requested database. The prior note claiming ~15-minute provisioning is **wrong or outdated**.

**Consequence**: the `emulated` flag existed only to skip slow RDS. Its remaining dependents must be reviewed
and the flag removed if nothing else needs it (Constitution VI — delete rather than keep "just in case").

**⚠️ A latent production bug this exposed**: `database.tf` hardcodes `:5432` in the JDBC URL, but Floci's RDS
endpoint is **Floci itself proxying a nonstandard port** (observed `172.22.0.2:7001` → the container's 5432).
The URL must use the created instance's `port` attribute. This also fixes real AWS, where a non-default port
is currently ignored (FR-006).

---

## R6. Cloud Map is the one real gap — and Route 53 is no escape

**Decision**: after ECS places a task, attach a Docker network alias carrying the production hostname
(`<svc>.<client>-<env>.internal`). The deployed task definitions and environment variables are **not** changed.

**Rationale**: the module injects peer URLs such as `OPERATIONS_URL=http://operations.acme-stg.internal:8083`
and relies on Cloud Map to resolve them. On Floci:

| Attempt | Result |
|---|---|
| `create-private-dns-namespace` + `create-service` | Succeed, return real ids |
| ECS `--service-registries` | Accepted, but **no instance is auto-registered** (`Instances: []`) |
| `servicediscovery register-instance` | Succeeds; `list-instances` shows it; name still **NXDOMAIN** |
| `route53 create-hosted-zone` + `change-resource-record-sets` | Return `INSYNC`; name still **NXDOMAIN** |
| Cloud Map namespace → Route 53 hosted zone | Not created (`list-hosted-zones` empty) |
| Docker network alias | **Resolves, and serves HTTP on the exact production hostname** |

All of the above were retested **with Floci as the resolver**, alongside a passing ALB control in the same
container — so this is isolated to Cloud Map, not another resolver mistake. The upstream feature is titled
"add AWS Cloud Map (servicediscovery) **management API**", consistent with the measurements.

**This is a silent failure**, which is why the spec is built around it: Terraform applies cleanly, `state
list` verifies, every health check passes, and every by-name cross-service call fails. The existing
`deploy-check.sh` would report success.

**Alternatives considered and rejected**:

- **Nginx as an internal router** — solves routing, not naming. The failure happens at DNS resolution before
  any proxy is reached, so a resolver is still required, plus a component production does not have.
- **A side-car DNS server (CoreDNS/dnsmasq)** — a real mechanism, but two more components to run and keep in
  sync with placement, versus one Docker flag.
- **Rewriting peer URLs to IPs** — changes deployed configuration (abandoning parity), IPs are unknown at
  task-definition time, and they churn on every task replacement.

---

## R7. Only the frontend is public — and it addresses the edge by a bare hostname

**Decision**: `frontend` is the sole `public` release-set entry; the alias set includes **both** the
production FQDN and the bare service name.

**Rationale**: `frontend/nginx.conf` proxies `/auth/` and `/api/` to `http://edge` — a **bare hostname**, not
the `edge.<client>-<env>.internal` FQDN the module injects. Aliasing only the FQDN would leave the frontend
unable to reach the edge, presenting as a dead UI with every service healthy.

This mirrors production, where the browser reaches only the frontend and everything else is private.

**Alternatives considered**: templating `nginx.conf` from the injected URL at build time — cleaner long-term
and worth doing later, but it changes an application artifact, so it is out of scope here.

---

## R8. Verification must exercise traffic, not resource existence

**Decision**: verification performs real HTTP against the running system, and must include (a) a
cross-service action and (b) an action that is *refused* for an unpermitted caller.

**Rationale**: this session found **two** defects that resource-existence checking would have passed — the
Cloud Map NXDOMAIN (R6), and the ALB conclusion that was wrong in the other direction (R1/R2). 010's
`deploy-check.sh` verifies with `terraform state list`. Health endpoints are also insufficient: in 017 every
service was individually healthy while every orchestrated write was refused because roles were not forwarded
between services.

The permission-refusal check exists because a deployment that silently grants everything would otherwise
look identical to a correct one (FR-025, SC-010).

---

## R9. Deployable units

**Decision**: 9 units — `frontend` (public), `edge`, `identity`, `gateway`, `operations`, `hr`, `crm`,
`procurement`, `workflow` (all private).

**Rationale**: derived from `backend/settings.gradle.kts` plus the Compose build definitions.
`reference-service` is an empty skeleton and `session-verify` is a library, so neither is deployable.
The module's `gateway_key` prefers a unit literally named `gateway` as the public entry, so that
preference must be overridden or the naming reconciled — otherwise the ALB fronts the wrong unit.

**Open item for `/speckit-tasks`**: whether both `edge` and `gateway` are needed in a single-client
deployment, or whether the edge alone suffices. The simulation runs both because it serves two isolated
clients; this feature deploys one.
