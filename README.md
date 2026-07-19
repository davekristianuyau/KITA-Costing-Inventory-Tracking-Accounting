# KITA — Costing, Inventory Tracking, and Accounting

A business application in the Odoo / SAP-HANA class, built as a **multi-service system** and
deployable to AWS, Google Cloud, or Azure. Managed with Spec Kit — specifications, plans, and tasks
live under [`specs/`](specs/).

## Architecture

```
Cloud Load Balancer (TLS, DNS, custom domain)         ← deploy: feature 001 (Terraform, multi-cloud)
        │  (only the gateway/frontend are public)
        ▼
Nginx edge  ── serves the built React app, caching, and proxies /api/* ───┐
        ▼                                                                  │ private network
Spring Cloud Gateway  ── routes /api/** to backend services ──────────────┤
        ▼                                                                  │
Spring Boot microservices  (operations-service, …)  ──────────────────────┤
        ▼                                                                  │
PostgreSQL  (one managed DB per environment; Flyway migrations per service)◄┘
```

The app deploys as a coordinated **Release Set** of container images (one per service). Only the
gateway/frontend are public; backend services stay on the private network. Customer/supplier
master data lives in a separate **Party service** (referenced by ID).

## Services

| Service | Path | Tech | Status |
|---------|------|------|--------|
| Frontend | [`frontend/`](frontend/) | React + TypeScript + Vite; login page + auth context + protected routing, served by Nginx | **implemented + tested** |
| Identity service | [`backend/identity-service/`](backend/identity-service/) | Spring Boot; BCrypt auth, user/client directory, asymmetric (RS256) + JWE session token | **implemented + tested** |
| Edge gateway | [`backend/edge-gateway/`](backend/edge-gateway/) | Spring Cloud Gateway; single public edge, verifies the session + routes `/api` by tenant `client` claim | **implemented + tested** |
| API gateway (per-client) | [`backend/gateway/`](backend/gateway/) | Spring Cloud Gateway; routes `/api/**` to services + rejects foreign `client` claims (`CLIENT_ID`) | **implemented** |
| Session verify | [`backend/session-verify/`](backend/session-verify/) | shared library: JWE-decrypt + RS256-verify of the session token (edge + gateways) | **implemented + tested** |
| **Operations service** | [`backend/operations-service/`](backend/operations-service/) | Spring Boot + JPA + Flyway; inventory, sales, BOM, production, costing | **implemented + tested** |
| Reference service | [`backend/reference-service/`](backend/reference-service/) | template for new services | scaffold |
| PostgreSQL | (Docker) | Postgres 16 | — |

> Additional implemented domain services (features 004–007): `hr-service`, `crm-service`,
> `procurement-service`, `workflow-service`. See [`specs/`](specs/).

The **operations-service** (feature 003) is the first real domain: item/UoM catalog, a stock
movement ledger (no negative stock), reservations (no oversell), bills of materials (kit &
manufactured, explosion), production builds, goods receipts, AVCO + FIFO/FEFO valuation, BOM cost
roll-up + margin, and availability/movement query APIs. See its
[README](backend/operations-service/README.md) and [SC coverage](backend/operations-service/src/test/README.md).

## Repository layout

| Path | Purpose |
|------|---------|
| `frontend/` | React + Nginx web frontend |
| `backend/` | Gradle multi-module backend (`gateway`, `reference-service`, `operations-service`) |
| `contracts/` | OpenAPI contracts (source of truth) |
| `infra/terraform/` | Multi-cloud deployment (feature 001; spec/plan complete, not yet implemented) |
| `docs/` | Architecture, add-a-service guide, quickstart |
| `docker-compose.yml` | Local multi-service orchestration |
| `Makefile` | `build` / `up` / `down` / `test` / `lint` |
| `specs/` | Spec Kit specs, plans, tasks (features 001–003) |

## Prerequisites

- **JDK 17** and **Docker Desktop** (running). Node 22 is needed only once the frontend app is built.
- The repo ships a Gradle wrapper (`backend/gradlew`) — no separate Gradle install required.

## Run the full backend stack (feature 008)

One command brings up the whole backend — API gateway + all backend services + a real **PostgreSQL 16**
database and **Redis 7.4** cache — health-gated, on a private network with **only the gateway exposed**
(`http://localhost:8081`). The containerized datastores are production-representative (pinned versions,
same migrations and config mechanism — see [`docs/parity.md`](docs/parity.md)); each service owns its own
schema in the single database.

```bash
cp .env.example .env      # local credentials (gitignored; never commit a real .env)
make up                   # build images + start the stack (docker compose up -d --build)
make ps                   # all services should be (healthy)

curl localhost:8081/actuator/health              # gateway UP
curl localhost:8081/api/operations/items         # routed: gateway → operations-service → DB

make down                 # stop, KEEP data   |   make clean = stop + remove data volumes
```

Verify scripts: [`scripts/stack-smoke.sh`](scripts/stack-smoke.sh) (build + health + schema isolation +
private datastores + gateway routing), [`scripts/verify-persistence.sh`](scripts/verify-persistence.sh)
(data survives a DB restart), [`scripts/check-parity.sh`](scripts/check-parity.sh) (pin + schema parity).
Full walkthrough: [`specs/008-docker-cache-database/quickstart.md`](specs/008-docker-cache-database/quickstart.md).

## Client login + multi-client simulation (feature 009)

A user logs in with **company + username + password**; the identity service authenticates (BCrypt) and issues
an **asymmetrically-signed, JWE-encrypted** session token in an httpOnly cookie (90-min lifetime). The
**edge gateway** — the single public entry — verifies it and routes `/api` to that user's **isolated client
backend** by the token's `client` claim, stripping/setting trusted `X-Kita-*` headers. Isolation is enforced
twice: at the edge, and again at each per-client gateway (which rejects any `client` ≠ its `CLIENT_ID`).
Because the token is asymmetric, no backend can forge a valid one.

[`sim/`](sim/) runs the whole model locally in Docker — two isolated feature-008 stacks (one Compose project
each) + identity + edge + frontend — and [`sim/aws-imitation/`](sim/aws-imitation/) "deploys" a client's AWS
stack with **real Terraform against [Floci](https://floci.io/)** (a local cloud emulator; no real cloud spend).

```bash
bash sim/sim-up.sh                          # 2 client stacks + identity + edge + frontend (only frontend exposed)
open http://localhost:8080/login            # company=client-a user=alice / company=client-b user=bob (demo-pass)
bash sim/sim-smoke.sh                        # health + isolation + per-client login → own backend
bash sim/aws-imitation/deploy.sh client-a   # real Terraform → Floci (no real cloud); then verify.sh
bash sim/sim-down.sh                         # independent teardown
```

Full walkthrough: [`specs/009-client-login-deploy-sim/quickstart.md`](specs/009-client-login-deploy-sim/quickstart.md).

## Run a single service (development)

Run one service against a local PostgreSQL (opt into a DB host port via `docker-compose.override.yml`):

```bash
docker compose up -d postgres redis
cd backend && ./gradlew :operations-service:bootRun   # Flyway migrates into its own schema on startup
```

Then it serves at `http://localhost:8083/api/operations`:

```bash
curl localhost:8083/api/operations/health
# create a unit of measure, item, location, then seed/query stock:
curl -XPOST localhost:8083/api/operations/uoms      -H 'content-type: application/json' -d '{"code":"ea","family":"COUNT"}'
curl -XPOST localhost:8083/api/operations/items     -H 'content-type: application/json' -d '{"sku":"WIDGET","name":"Widget","type":"FINISHED_GOOD","baseUom":"ea"}'
```

Key endpoints (base `/api/operations`): `items`, `uoms`, `locations`, `adjustments`,
`items/{id}/availability`, `movements`, `boms` (+ `/{id}/explosion`), `builds`, `sales-orders`
(+ `/{id}/confirm|fulfill|cancel`), `receipts`, `items/{id}/cost`. Contract:
[`specs/003-sales-inventory-bom/contracts/operations-openapi.yaml`](specs/003-sales-inventory-bom/contracts/operations-openapi.yaml).

### Make targets
```bash
make up      # build + start the full stack (gateway + services + postgres + redis)
make down    # stop the stack (keeps data volumes)
make clean   # stop + remove data volumes (clean slate)
make ps      # stack status   |   make logs = tail logs
make test    # backend tests
make lint    # Spotless + Checkstyle
```

## Testing

```bash
cd backend && ./gradlew :operations-service:test
```

Integration tests use **Testcontainers**, which needs a running Docker daemon (a throwaway
`postgres:16-alpine` container is started per run). On **Linux/macOS/CI** the default socket works.

On **Windows + Docker Desktop**, the bundled docker-java client can't use the named pipe, so the
test task (in `operations-service/build.gradle.kts`, gated to Windows only) points Testcontainers at
a TCP daemon — enable **Docker Desktop → Settings → General → "Expose daemon on
tcp://localhost:2375 without TLS"**. CI (`.github/workflows/ci.yml`) runs on Linux and needs none
of this.

## Deployment

Multi-cloud deployment is **feature 001** (`infra/terraform/`): a single Terraform codebase with
provider-selectable modules (AWS ECS Fargate / GCP Cloud Run / Azure Container Apps), STG + PROD
tiers, gated Release-Set promotion, and auto-rollback. Spec, plan, and tasks are complete; the
Terraform itself is not yet implemented. See [`specs/001-multi-cloud-cicd`](specs/001-multi-cloud-cicd/).

## Status

- **Implemented**: operations-service (feature 003) — full domain, tested against real PostgreSQL.
- **Scaffold**: frontend, gateway, reference-service (feature 002).
- **Planned**: multi-cloud deployment (feature 001); Party and Accounting services (future specs).
