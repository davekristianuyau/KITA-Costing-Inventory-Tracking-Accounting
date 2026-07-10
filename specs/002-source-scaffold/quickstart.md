# Quickstart: Verifying the Scaffold

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

This feature is scaffolding only, so the "quickstart" is about **verifying the skeleton**, not
running an application. There is deliberately nothing to build or boot yet.

## 1. Inspect the structure

From a clean checkout, list the tree and confirm every planned component has a home:

```text
frontend/        (React+Nginx scaffolding: package.json, configs, nginx.conf, Dockerfile)
backend/
  settings.gradle.kts, build.gradle.kts, config/
  gateway/            (build skeleton + Dockerfile + placeholder src)
  reference-service/  (TEMPLATE: build skeleton, Dockerfile, src/…, resources/db/migration/)
contracts/openapi.yaml
docs/            (architecture.md, add-a-service.md, quickstart.md)
docker-compose.yml, Makefile, README.md, .gitignore
```

Expected: each component has a dedicated directory; empty directories carry a `.gitkeep` or
README (SC-001, SC-004).

## 2. Confirm scaffolding files are present

Check the Scaffolding File inventory in [data-model.md](./data-model.md); every listed file
exists (SC-002). Open a few to confirm they are skeletons/config only:

- `backend/settings.gradle.kts` declares `:gateway` and `:reference-service`.
- `docker-compose.yml` names postgres, gateway, reference-service, frontend and references each
  Dockerfile.
- `Makefile` documents `build`/`up`/`test`/`lint` as placeholder targets.
- `contracts/openapi.yaml` is present as the source-of-truth contract.

## 3. Confirm the scope guard (no code)

Verify the repository contains **no** application source, functional tests, or migration files
(SC-003):

- Service source directories contain only placeholders.
- `db/migration/` is empty except for its placeholder.
- No test suites with assertions exist.

## 4. Read the documentation

- `README.md` describes the architecture (cloud LB → Nginx → gateway → services → PostgreSQL)
  and the layout (SC-005).
- `docs/add-a-service.md` explains how a future service is added using the reference-service
  template (SC-006).

## Acceptance mapping

| Step | Validates |
|------|-----------|
| 1 | US1, FR-001/002/010/014, SC-001/004 |
| 2 | US2/US3, FR-003–009/012, SC-002 |
| 3 | Scope guard, FR-013, SC-003 |
| 4 | US4, FR-011, SC-005/006 |

## What comes next (not this feature)

Later per-service features fill in this skeleton following TDD: implement the reference
microservice (with Flyway migrations), the gateway routing, and the React UI, then wire the
Makefile/compose into a genuinely buildable, runnable, tested system.
