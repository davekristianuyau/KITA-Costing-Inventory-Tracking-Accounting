# Contract: the release set

The nine deployable units and their declared shape. This map is passed to
`infra/terraform/modules/aws`; **the field shape is the module's, not this feature's**, so it must not be
forked or extended locally (FR-002).

## Declared units

| Key | Visibility | Port | Health path | Notes |
|---|---|---|---|---|
| `frontend` | **public** | 8080 | `/healthz` | The only ALB target. Proxies `/auth/` and `/api/` to the edge. |
| `edge` | private | 8080 | `/actuator/health` | Session verification, header stripping, per-request role resolution |
| `identity` | private | 8090 | `/actuator/health` | Accounts, sign-in, session tokens |
| `gateway` | private | 8081 | `/actuator/health` | In-stack router — see open question below |
| `operations` | private | 8083 | `/actuator/health` | Catalog, inventory, BOM, sales, costing |
| `hr` | private | 8085 | `/actuator/health` | Employees, payroll, account↔employee links and roles |
| `crm` | private | 8086 | `/actuator/health` | Customers, discounts |
| `procurement` | private | 8087 | `/actuator/health` | Suppliers, purchase orders, receiving |
| `workflow` | private | 8088 | `/actuator/health` | Back-office orchestration, maker–checker |

Ports are taken from each service's `application.yml`; a mismatch between declaration and what the service
binds fails the health check.

## Rules

1. **Exactly one public unit.** Zero makes the system unreachable; more than one contradicts the design in
   which the browser only ever reaches the frontend (R7).
2. **`gateway_key` must resolve to the frontend.** The module currently prefers a unit literally named
   `gateway` as its public entry (`main.tf`). Left alone, the ALB would front the wrong unit. Either the
   preference is overridden or the naming is reconciled — a task-level decision, but it **must** be made
   deliberately.
3. **One version tag per local run**, so what the run proves is unambiguous.
4. **Image references point at ECR**, not the local build cache (FR-004).
5. **Every key needs a build definition.** Eight come from existing Compose build contexts
   (`backend/*/Dockerfile`, `frontend/Dockerfile`); none may be invented here.

## Internal addressing

Each unit is reachable at `{key}.{client}-{env}.internal:{port}`, and the module injects those URLs into
every unit as `{KEY}_URL` environment variables. **This declaration is production's, unchanged** — the local
substitute for resolving those names lives entirely in the deployment tooling (R6).

Because `frontend/nginx.conf` proxies to the bare hostname `http://edge`, the short name must resolve too,
not only the FQDN.

## Open question for `/speckit-tasks`

Whether a single-client deployment needs **both** `edge` and `gateway`. The simulation runs both because it
fronts two isolated client stacks; this feature deploys one. If the edge can route directly to services,
`gateway` drops out of the release set — but that changes the request path away from what the simulation and
production use, so it is a deliberate decision, not a cleanup. Resolve before implementing US1.
