# Contract — Runtime & Configuration

Governs how the stack is configured and exposed. Enforced by the compose file, `.env.example`, and
service `application.yml`.

## Configuration keys (per container)

- **Postgres**: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (required; no committed default).
- **Redis**: no auth locally; `REDIS_PASSWORD` optional (required in prod parity).
- **Each backend service**: `DATABASE_URL` (includes `?currentSchema=<svc>,public`), `DATABASE_USER`,
  `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`. Service-specific stub flags (e.g. `PARTY_STUB`) keep
  their existing defaults.
- **Gateway**: `<SVC>_SERVICE_URL` for every routed backend service.

## Rules

- **R1 — No secrets in repo**: only `.env.example` (placeholders) is committed; real `.env` is gitignored.
- **R2 — Fail fast**: a required secret that is absent MUST stop the affected container with a clear log,
  never start with an insecure built-in default outside local dev.
- **R3 — Exposure**: only the gateway publishes a host port (`8081`). Postgres, Redis, and all backend
  services are reachable only on the internal `kita` network. Dev may opt into DB/Redis host ports via a
  local `docker-compose.override.yml` (not committed).
- **R4 — Parity**: the same keys and the same `currentSchema` value are used locally and in production;
  only values differ. Engine image tags are pinned identically (Postgres 16, Redis 7.4).
- **R5 — Teardown**: `down` stops containers and keeps volumes; `down -v` is the only path that removes
  data volumes (explicit clean slate).

## Acceptance

- Grep of the repo finds no real credential; `.env.example` present. (SC — Security)
- External connection to Postgres/Redis/any backend service is refused; gateway answers on 8081. (SC-005)
- Switching `.env` between local and a prod-shaped file needs no code/engine/migration change. (SC-008)
