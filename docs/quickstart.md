# Quickstart — Verifying the Scaffold

**Status: scaffolding.** Nothing runs yet. This guide verifies the skeleton is complete and
well-formed. Running the application comes after the services are implemented.

## 1. Inspect the structure

```
frontend/        React + Nginx scaffolding (package.json, configs, nginx.conf, Dockerfile)
backend/         Gradle multi-module: settings + build + config/
  gateway/            module skeleton + Dockerfile + application.yml
  reference-service/  TEMPLATE: module skeleton, Dockerfile, application.yml, db/migration/
contracts/openapi.yaml
docs/            architecture.md, add-a-service.md, quickstart.md
docker-compose.yml, Makefile, README.md, .gitignore, .env.example
```

Each component has a dedicated directory; empty directories carry a `.gitkeep`.

## 2. Confirm scaffolding files are present

Open a few to confirm they are config/skeletons only:

- `backend/settings.gradle.kts` declares `:gateway` and `:reference-service`.
- `docker-compose.yml` names postgres, gateway, reference-service, frontend and references each
  Dockerfile.
- `Makefile` documents `build`/`up`/`test`/`lint` as placeholders.
- `contracts/openapi.yaml` is present as the source-of-truth API contract.

## 3. Confirm the scope guard (no code)

- Service source directories contain only `.gitkeep` placeholders.
- `backend/reference-service/src/main/resources/db/migration/` has no migration files.
- No test suites exist.

## 4. Prerequisites for later (informational)

When implementation begins you will need: Docker + Docker Compose, Node 22 LTS, and JDK 21.
`make help` lists the documented commands.

## Next

Implement the reference microservice, gateway, and frontend (TDD) in later features, then
activate `make build` / `make up` / `make test` / `make lint`.
