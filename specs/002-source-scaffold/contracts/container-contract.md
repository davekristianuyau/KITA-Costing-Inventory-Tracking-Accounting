# Contract: Per-Service Container Image

**Feature**: 002-source-scaffold | **Date**: 2026-07-08

The contract each service image satisfies so feature `001-multi-cloud-cicd` can deploy it. One
image per service (FR-005/006). This is what feature 001's Release Set consumes.

## Every service image MUST

| Aspect | Requirement |
|--------|-------------|
| Build | Produced by the service's `Dockerfile`; reproducible from a clean checkout (FR-001). |
| Tag | Carry an immutable version tag/digest (FR-006). No floating `latest` for deploys. |
| Port | Listen on a single documented port provided via env (default per service below). |
| Health | Expose a health endpoint returning `UP`/`DOWN` (+ version) — JVM: Actuator `/health`; frontend: Nginx health location. |
| Config | Read all configuration from environment variables (FR-014); no baked-in secrets or URLs. |
| Logs | Write structured (JSON) logs to stdout. |
| Statelessness | Hold no local persistent state; all durable data goes to PostgreSQL / object storage. |

## Per-service specifics

| Service | Image contents | Default port | Public? | Key env |
|---------|----------------|--------------|---------|---------|
| `frontend` | Nginx serving built React assets + `/api` proxy | 8080 | yes | `GATEWAY_URL` |
| `gateway` | Spring Cloud Gateway | 8081 | yes | `REFERENCE_SERVICE_URL`, route config |
| `reference-service` | Spring Boot + JPA + Flyway | 8082 | no (private) | `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` |

## Release Set

The set of `{service → version}` deployed together is expressed in a versioned manifest
(`release-set.yaml` / Compose `.env`). Feature 001 deploys and promotes this set as one unit
(FR-016); a running environment never mixes versions outside a declared Release Set.

## Health aggregation

The gateway's `/health` reports `UP` only when it and all required downstream services report
`UP`; otherwise `DOWN`. This is the aggregate signal feature 001 gates deployments on.
