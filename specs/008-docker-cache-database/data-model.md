# Data Model — Containerized Database & Cache Runtime

This feature adds no business entities; it defines the **runtime topology**, **schema ownership**, and
**cache model** the services run against.

## Schema ownership (single database `kita`)

Each service owns exactly one schema; it writes only that schema. Flyway history table lives per schema.

| Service | Schema | Search path (`currentSchema`) | Owns |
|---|---|---|---|
| operations-service | `operations` | `operations,public` | catalog, inventory, BOM, sales, procurement, party, costing |
| hr-service | `hr` | `hr,public` | employees, payroll, deductions, leave, time |
| crm-service | `crm` | `crm,public` | customers, discounts, loyalty |
| procurement-service | `procurement` | `procurement,public` | suppliers, POs, receiving |
| workflow-service | `workflow` | `workflow,public` | activity log, authorization mapping |

> `reference-service` is an empty spec-002 skeleton (no code/migrations) and is **excluded** from this
> feature — no schema, not wired into the stack — until it is implemented.

**Rules**: writes are schema-local (FR-020); cross-service data via the owning service's API (FR-021);
`public` holds shared extensions only (`pgcrypto`), created at DB init before any migration runs.

## Container topology

| Container | Image (pinned) | Network | Host-exposed | Depends on (healthy) | Health probe |
|---|---|---|---|---|---|
| postgres | `postgres:16-alpine` | kita | no (opt-in override) | — | `pg_isready` |
| redis | `redis:7.4-alpine` | kita | no (opt-in override) | — | `redis-cli ping` |
| operations-service | built | kita | no | postgres, redis | `/actuator/health` |
| hr-service | built | kita | no | postgres | `/actuator/health` |
| crm-service | built | kita | no | postgres | `/actuator/health` |
| procurement-service | built | kita | no | postgres | `/actuator/health` |
| workflow-service | built | kita | no | postgres | `/actuator/health` |
| gateway | built | kita | **8081 only** | operations/hr/crm/procurement/workflow | `/actuator/health` |

> reference-service is not started by this feature (dormant skeleton).

## Volumes (persistence — FR-004)

| Volume | Backs | Removed by |
|---|---|---|
| `kita-pgdata` | Postgres data dir (`/var/lib/postgresql/data`) | `docker compose down -v` (explicit clean slate only) |
| `kita-redisdata` (optional) | Redis append-only file | `docker compose down -v` |

`docker compose down` (no `-v`) keeps data; `down -v` is the explicit clean-slate path (FR-009).

## Cache model (Redis, non-authoritative)

| Cache | Key | Value | TTL | Populated by | Invalidated by |
|---|---|---|---|---|---|
| `catalog:item` (demonstrator) | item/catalog id | serialized read model | bounded (e.g. 10 min) + on-write evict | `@Cacheable` catalog read (operations-service) | `@CacheEvict` on the matching catalog write |

**Invariants**: cache holds copies only; a completed write evicts/refreshes before the next read
(FR-014, no stale reads); Redis unavailable ⇒ reads fall through to Postgres (FR-013). Other services
may add their own caches later following this same pattern.

## Environment configuration matrix (same keys, env-scoped values — FR-012)

| Key | Local (`.env`) | Production (secret store) |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | dev values | prod secret |
| `DATABASE_URL` per service | `jdbc:postgresql://postgres:5432/kita?currentSchema=<svc>,public` | managed host, same `currentSchema` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | dev values | prod secret |
| `REDIS_HOST` / `REDIS_PORT` | `redis` / `6379` | managed/host cache |
| `<SVC>_SERVICE_URL` (gateway routes) | internal DNS names | env-scoped |

No key is hard-coded in images or compose; missing required secrets fail fast (FR-010/FR-016).
