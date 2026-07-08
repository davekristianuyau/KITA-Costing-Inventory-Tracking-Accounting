# KITA — Costing, Inventory Tracking, and Accounting

A business application (Odoo/SAP-HANA class) built as a multi-service system and deployable to
AWS, Google Cloud, or Azure. This repository is managed with Spec Kit; specifications live under
[`specs/`](specs/).

> **Status: scaffolding.** This repository currently contains the project **skeleton** —
> folder structure and config/skeleton files only. There is no application code yet. See
> feature [`specs/002-source-scaffold`](specs/002-source-scaffold/spec.md).

## Architecture

Request path (in the cloud, per feature 001):

```
Cloud Load Balancer (TLS/DNS)  →  Nginx edge (static React + cache + /api proxy)
                               →  Spring Cloud Gateway (API routing)
                               →  Spring Boot microservices  →  PostgreSQL
```

- **Frontend** — React + TypeScript (Vite), served by Nginx. See [`frontend/`](frontend/).
- **API gateway** — Spring Cloud Gateway. See [`backend/gateway/`](backend/gateway/).
- **Microservices** — Java/Spring Boot; one **reference service** is the template for future
  domains (costing, inventory, accounting). See [`backend/reference-service/`](backend/reference-service/).
- **Contract** — OpenAPI, source of truth in [`contracts/openapi.yaml`](contracts/openapi.yaml).
- **Persistence** — PostgreSQL with Flyway migrations (per service, shared DB per environment).

## Repository layout

| Path | Purpose |
|------|---------|
| `frontend/` | React + Nginx web frontend (scaffolding) |
| `backend/` | Gradle multi-module backend: `gateway`, `reference-service` |
| `contracts/` | OpenAPI API contract (source of truth) |
| `docs/` | Architecture, add-a-service guide, quickstart |
| `docker-compose.yml` | Local multi-service orchestration (skeleton) |
| `Makefile` | Documented `build`/`up`/`test`/`lint` commands (skeleton) |
| `specs/` | Spec Kit specifications, plans, and tasks |

## Getting started

This is scaffolding — nothing runs yet. To verify the skeleton, see
[`docs/quickstart.md`](docs/quickstart.md). Implementation of each service follows in later
features (TDD), filling in this structure.
