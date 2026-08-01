# edge-gateway

The **authenticated public entry point** (spec 009). Verifies the session cookie, resolves the client to
its backend, strips anything the browser tried to assert, and routes `/api/**` to that tenant's stack.

- **Package**: `com.kita.edge` · Java 17 / Spring Boot 3.5 / Spring Cloud Gateway (reactive).
- `/auth/**` and health pass through untouched; everything else needs a valid session.

## What it asserts on every request

| Header | Source |
|---|---|
| `X-Kita-User` | the validated session subject — the signed-in **account** |
| `X-Kita-Client` | the validated `client` claim, used to pick the backend |
| `X-Kita-Roles` | **resolved from the personnel record, per request** (017) |

**Every inbound `X-Kita-*` is stripped first.** That is what makes these headers trustworthy downstream,
and it is why the session identity cannot be forged by a browser (017 FR-003). `EdgeRoutingIT` asserts
the spoof case directly — keep it green when touching `SessionAuthFilter`.

## Roles are resolved, never carried in the token (017 FR-018)

`RoleResolver` calls `GET /api/hr/employees/by-account/{username}` on **every** request and sets the
trusted role header from the answer.

The obvious alternative — filling the session token's (currently unused) `roles` claim at login — is
deliberately rejected: a token carrying authority keeps its privileges when stolen, and a revoked role
would survive until expiry. Resolving beside the session keeps roles server-side and fresh, so a
revocation bites on the **next request** (SC-002/SC-006). For the same reason the lookup is **not cached**.

Failure handling is asymmetric on purpose:

- hr unreachable / 5xx → **503, fail closed**. Never grant access on a failed lookup (FR-011).
- account with no employee, or a non-`ACTIVE` one → **no roles**, request proceeds. The receiving service
  then refuses on its own terms, which keeps "you may not do this" distinguishable from "we could not check".

## Startup preflight (FR-019)

`OwnerPreflight` warns loudly if no account resolves to an employee holding `OWNER`. With the permissive
fallback retired, that state is **unadministerable** — nobody can link an account or grant the first role —
and it would otherwise present as "the system is broken". It warns rather than refuses to start, so an
operator can still reach `/auth/**` to fix it. Disable with `edge.preflight.owner-check=false`.

## Configuration

| Property | Purpose |
|---|---|
| `edge.backends.<client>` | that client's backend base URL (no fallback — unknown client → 401) |
| `edge.hr.base-url` | personnel service for role resolution |
| `edge.hr.timeout-ms` | role-lookup timeout (default 2000); a timeout fails **closed** |
| `edge.jwt.public-key` / `edge.jwt.enc-key` | session verification material |
| `edge.cookie.name` | session cookie name |

```bash
cd backend && ./gradlew :edge-gateway:test    # routing, spoof-stripping, and role resolution
```
