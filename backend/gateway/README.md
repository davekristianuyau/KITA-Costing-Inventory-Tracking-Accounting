# backend/gateway/

**Spring Cloud Gateway** — the single backend entry point that routes `/api/*` to the
microservices and is the future home for auth, rate limiting, and circuit breaking.

**Status: scaffolding only.** Holds a module build skeleton, an `application.yml` with the
route pattern commented out, and a `Dockerfile` skeleton. Routing logic and health aggregation
are implemented in a later feature.
