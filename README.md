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
| Frontend | [`frontend/`](frontend/) | React + TypeScript + Vite, served by Nginx | scaffold |
| API gateway | [`backend/gateway/`](backend/gateway/) | Spring Cloud Gateway (routes `/api/operations/**`) | scaffold |
| **Operations service** | [`backend/operations-service/`](backend/operations-service/) | Spring Boot + JPA + Flyway; inventory, sales, BOM, production, costing | **implemented + tested** |
| Reference service | [`backend/reference-service/`](backend/reference-service/) | template for new services | scaffold |
| PostgreSQL | (Docker) | Postgres 16 | — |

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

## Run locally (development)

Run the operations-service against a local PostgreSQL:

```bash
# 1) start PostgreSQL (published on localhost:5432, db/user/pass = kita/kita/change-me)
docker compose up -d postgres

# 2) run the service (Flyway migrates on startup; Party validation uses the dev stub)
cd backend && ./gradlew :operations-service:bootRun
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
make up      # docker compose up -d (postgres + services)
make down    # stop the stack
make test    # backend tests (operations-service)
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
