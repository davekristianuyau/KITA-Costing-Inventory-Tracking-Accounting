# Contract — Health & Startup Ordering

Governs readiness signalling and dependency-ordered bring-up.

## Health probes

- **Postgres**: healthy when `pg_isready` succeeds.
- **Redis**: healthy when `redis-cli ping` returns `PONG`.
- **Every backend service + gateway**: healthy when `GET /actuator/health` returns `200 {"status":"UP"}`.
  (Requires enabling the actuator dependency on gateway and reference-service.)

## Startup ordering

- **S1**: Postgres init runs `CREATE EXTENSION IF NOT EXISTS pgcrypto` in `public` before any service
  migrates.
- **S2**: Each backend service starts only after Postgres (and, where it uses the cache, Redis) reports
  **healthy** (`depends_on: condition: service_healthy`).
- **S3**: The gateway starts only after all routed backend services report healthy.
- **S4**: A service MUST NOT report healthy or accept traffic until its own migrations have applied and
  its datastore connections are established.

## Failure behavior

- Missing secret, unreachable dependency, host-port conflict, or an incompatible existing data volume MUST
  produce a structured, actionable log line and stop that container rather than failing silently (FR-016).
- A datastore that restarts mid-run: dependent services reconnect automatically once it is healthy again.

## Acceptance

- From clean, `docker compose up` reaches all-healthy with no manual DB/cache steps in < 10 min. (SC-001)
- A request through the gateway returns a correct DB-backed response on a freshly healthy stack. (SC-003)
- Stopping/starting the data tier loses no committed data. (SC-002)
