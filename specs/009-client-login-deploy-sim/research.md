# Phase 0 Research — Client Login & Per-Client Deployment Simulation

## D1. Authentication & password storage

- **Decision**: **Company + username + password** verified by `identity-service` using **BCrypt**-hashed
  passwords (Spring Security `PasswordEncoder`); usernames are unique **within a client**, so company + username
  resolve exactly one user (FR-019). Repeated failures are throttled (per-account attempt counter + lockout).
- **Rationale**: FR-002/009/010, Constitution III. BCrypt is the standard adaptive hash; Spring Security gives
  throttling hooks and safe comparison.
- **Alternatives**: plaintext/reversible (rejected — never); external IdP/OAuth provider (rejected — heavier,
  and the spec keeps auth centralized in-house; MFA/SSO are explicitly out of scope).

## D2. Session token & signing scheme

- **Decision** (per clarification): A **JWT signed asymmetrically** (RSA/EC) — identity signs with its
  **private key**; the edge and every client backend verify with the **public key only**. Claims: `sub`,
  `client`, `roles?`, `iat`, `exp` (**90-minute** absolute lifetime → forced re-auth), `jti`. The token is
  additionally **encrypted at rest** in the browser (see D7).
- **Rationale**: Asymmetric signing means no client backend can mint a valid token for another client (FR-020,
  SC-008) — the isolation guarantee holds even if a backend is compromised. Stateless verification everywhere
  (FR-006); 90-min TTL bounds replay.
- **Alternatives**: **HMAC shared secret** (rejected per clarification — every backend holding the secret could
  forge cross-tenant tokens); opaque server-side sessions (rejected — shared session store couples tenants);
  long-lived tokens (rejected — replay risk).

## D3. Tenant routing at the edge

- **Decision**: `edge-gateway` (Spring Cloud Gateway, as in 008) exposes `/auth/**` → `identity-service` and
  `/api/**` → the client backend. An auth filter validates the JWT, reads the `client` claim, dynamically
  routes to `http://<client>-gateway:8081`, and **strips any client-supplied identity headers** then sets
  trusted `X-Kita-User` / `X-Kita-Client` from the token — which the backend services already consume.
- **Rationale**: FR-004/005; reuses proven Spring Cloud Gateway; routing by claim keeps a single frontend
  origin (no browser CORS across many client URLs). Header stripping prevents spoofing.
- **Alternatives**: identity returns each client's URL and the browser calls it directly (rejected — multi-origin
  CORS, exposes internal endpoints); a bespoke reverse proxy (rejected — YAGNI, gateway already does this).

## D4. Per-client isolation enforced twice

- **Decision**: Isolation is **physical** (each client is a separate feature-008 stack: own gateway, services,
  Postgres, Redis, network, volumes) **and** logical (a client's gateway is configured with its own `client`
  id and rejects a JWT whose `client` claim differs — FR-006). The edge routes strictly by claim.
- **Rationale**: SC-002 (0 cross-tenant), the security core. Physical isolation makes leakage structurally
  hard; the claim check stops a valid-looking token being replayed at the wrong backend.
- **Alternatives**: one backend with row-level tenant scoping (rejected — weaker isolation, contradicts the
  001 per-client-deployment model this feature is meant to prove).

## D5. Two isolated client stacks on one host (sim topology)

- **Decision**: Run the feature-008 `docker-compose.yml` **twice as separate Compose projects**
  (`docker compose -p kita-client-a` / `-p kita-client-b`) — each gets its own network, DNS, and volumes with
  **no reuse/duplication** of the 008 file. A `client-overlay.yml` removes the gateway host port (two projects
  can't both bind 8081). The edge project (identity + edge + frontend + LocalStack) attaches to both client
  networks and routes to the client gateways by **container name** (`kita-client-a-gateway-1`, etc.).
- **Rationale**: Reuses 008 verbatim, keeps each client genuinely isolated, one shared edge on top. `sim-up.sh`
  orchestrates the multi-project bring-up.
- **Alternatives**: a hand-written mega-compose duplicating all services per client (rejected — verbose,
  drifts from 008); Docker network aliases on one shared network (rejected — service-name collisions).

## D6. LocalStack "AWS imitation" scope (US4)

- **Decision**: Treat **LocalStack** as the local AWS. Reuse the **001 Terraform AWS module** via `tflocal`
  (LocalStack's Terraform wrapper) to provision the AWS resources LocalStack **Community** supports for the
  chosen client (e.g. networking primitives, S3 for artifacts/state, Secrets Manager for DB credentials), with
  the client's running **feature-008 stack as the compute/DB stand-in** (ECS/Fargate/RDS are LocalStack **Pro**
  features and are represented, not emulated). This proves "client prefers AWS → the AWS deployment path runs
  end-to-end locally with no real cloud" (SC-005) and de-risks 001.
- **Rationale**: Honest within Community limits; exercises the real Terraform + AWS SDK calls locally; keeps the
  login flow reaching the client's locally-imitated deployment.
- **Alternatives**: LocalStack Pro for full ECS/RDS (rejected — license/cost, out of scope); skip Terraform and
  fake it (rejected — wouldn't demonstrate the real deploy path).
- **Open**: exact resource subset is finalized in tasks against the current 001 AWS module.

## D7. Frontend login & session handling

- **Decision** (per clarification): React login page (`/login`) collects **company + username + password** and
  posts to `/auth/login`. On success the edge/identity sets an **httpOnly, Secure cookie** holding the
  **encrypted** session token (JWE-wrapped) — so the session **survives refresh**, is unreadable by page
  scripts, and is encrypted at rest independent of TLS. Absolute **90-minute** lifetime forces re-auth; a
  protected-route wrapper redirects unauthenticated/expired users to `/login`; sign-out clears the cookie and
  calls `/auth/logout`. The edge reads the cookie and forwards trusted `X-Kita-*` to backends. nginx (scaffolded)
  serves the SPA and proxies `/auth` + `/api` to the edge.
- **Rationale**: FR-001/007/008/021; httpOnly cookie is XSS-resistant and survives refresh; JWE encryption at
  rest satisfies "encrypt the token even though transport is already encrypted".
- **Alternatives**: token in memory (rejected — lost on refresh); `localStorage` (rejected — readable by
  injected scripts); signed-but-unencrypted token in storage (rejected — clarification requires encryption).
