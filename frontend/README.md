# frontend/

KITA web frontend — **React + TypeScript + Vite**, served in production by **Nginx** (static
assets + HTTP caching + `/api` reverse proxy to the gateway).

**Status: scaffolding only.** This directory currently holds config/skeleton files
(`package.json`, `tsconfig.json`, `vite.config.ts`, lint/format configs, `nginx.conf`,
`Dockerfile`). Application components, routing, and tests are added in a later feature.

- `src/` — application source (placeholder).
- `tests/` — Vitest + React Testing Library suites (placeholder).
- API client is generated from `../contracts/openapi.yaml` via `npm run gen:api`.
