# Local ↔ Production Parity (spec 008)

The containerized database and cache are **production-representative**: what a developer runs locally
behaves the same as production, differing only by environment-scoped values. This is enforced by
`scripts/check-parity.sh` (a CI gate) and the rules below.

## Pinned engine versions (same major/minor everywhere)

| Component | Pinned image | Production |
|---|---|---|
| Database | `postgres:16-alpine` | the same image, or a managed PostgreSQL instance pinned to **16.x** |
| Cache | `redis:7.4-alpine` | the same image, or a managed Redis pinned to **7.4.x** |

Version drift is not allowed — `check-parity.sh` fails the build if the pins change. "Same as
production" means behavioral parity: identical engine major/minor, identical migrations, identical
configuration mechanism.

## Same configuration mechanism, env-scoped values only

Every service uses the identical keys and the identical connection shape in every environment; only the
**values** differ (hostnames, credentials, sizing):

- DB connection: `jdbc:postgresql://<host>:5432/<db>?currentSchema=<svc>,public` — the `currentSchema`
  and the schema-per-service layout are identical local and prod; only `<host>`/`<db>`/credentials change.
- Cache: `REDIS_HOST` / `REDIS_PORT` (+ `REDIS_PASSWORD` in prod).
- Secrets come from `.env` locally and the per-environment secret store (`{client}-{env}`) in prod — never
  committed. See `specs/008-docker-cache-database/contracts/runtime-config.md`.

Moving between environments requires **no code, engine, or migration change** — only swapping the
environment-scoped values (SC-008).

## Migrations

Each service owns versioned Flyway migrations that target its own schema (`default-schema=<svc>`,
`create-schemas=true`). The same migration history applies identically to the local containerized
database and to production through the same repeatable mechanism (SC-004).

## Production backup/restore (required before reliance) — FR-017

Because the production database holds financial and inventory data, a **restorable backup MUST exist
before the database is relied upon**. Operationally:

- Take an automated periodic backup (managed-instance snapshot or `pg_dump`), retained per policy.
- Verify restorability by periodically restoring into a scratch instance and running the app's
  migrations + a smoke read.
- Never rely on a production database that has not been proven restorable.

(Backup **scheduling/automation** and DR orchestration are out of scope for this feature — this documents
the standing requirement and the parity guarantees.)
