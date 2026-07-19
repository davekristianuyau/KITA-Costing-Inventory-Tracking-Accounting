# Data Model — Client Login & Per-Client Deployment Simulation

## Identity-service entities (its own database; no client business data)

### User
| Field | Notes |
|---|---|
| id | surrogate id |
| username | login identifier; **unique within a client** (same username may exist in other clients) |
| password_hash | **BCrypt**; never returned, never logged |
| client_id | FK → Client; a user belongs to exactly one client (FR-003) |
| active | disabled users cannot log in |
| failed_attempts / locked_until | brute-force throttling (FR-009) |

### Client (tenant)
| Field | Notes |
|---|---|
| id | tenant id (used as the JWT `client` claim + routing key) |
| name | display name |
| cloud_preference | `AWS` \| `GCP` \| `AZURE` — determines production target (FR-012, mirrors 001) |
| backend_endpoint | how the edge reaches this client's backend (e.g. `kita-<id>-gateway:8081`) |
| active | |

**Uniqueness rule** (per clarification): `username` is unique **within a client**; the login form collects a
**company/client identifier**, so company + username resolve exactly one user (edge case "two clients, same
username" is closed).

## Session token (JWT) — issued by identity, verified by edge + client backends

| Claim | Meaning |
|---|---|
| `sub` | user id/username |
| `client` | tenant id → routing key + isolation check |
| `roles` | optional; forwarded for backend authorization |
| `iat` / `exp` | issued-at / expiry (short TTL, ~30 min) |
| `jti` | token id (revocation / replay defense) |

**Signature**: **asymmetric** (identity's private key; edge + backends verify with the public key only — no
backend can mint tokens). **TTL**: 90-minute absolute lifetime (forced re-auth). **At rest in the browser**:
carried in an **httpOnly, Secure cookie** as an **encrypted** token (JWE). Verified statelessly at the edge and
at each client gateway.

## Session lifecycle (states)

`ANONYMOUS → (valid login) → AUTHENTICATED(client) → (sign-out | expiry | revocation) → ANONYMOUS`

- Invalid credentials / unknown user / locked → stays `ANONYMOUS`, clear non-revealing error (FR-008).
- `AUTHENTICATED` requests carry the JWT; the edge routes by `client`; a foreign-client backend rejects it.

## Forwarded identity headers (edge → client backend)

The edge **strips** any inbound `X-Kita-*` and sets, from the validated token: `X-Kita-User`,
`X-Kita-Client` (+ `X-Kita-Roles` if present). Backend services already consume these (existing
`CallerContext`), so no backend change is required beyond the per-client `client`-claim check at its gateway.

## Simulation topology (containers)

| Component | Role | Host-exposed |
|---|---|---|
| frontend (nginx) | serves SPA; proxies `/auth`,`/api` → edge | **yes** (only public surface) |
| edge-gateway | validates JWT; `/auth`→identity, `/api`→client backend by claim | no |
| identity-service | auth + user/client directory + JWT issuance | no |
| identity Postgres | identity store | no |
| **client-A stack** (feature-008) | gateway + 5 services + Postgres + Redis (Compose project `kita-client-a`) | no |
| **client-B stack** (feature-008) | same, project `kita-client-b`, fully isolated | no |
| localstack | local AWS imitation for the AWS-preferring client (US4) | dev only |

Isolation: client-A and client-B stacks share nothing (separate networks, DBs, volumes). The edge joins both
client networks to reach their gateways by container name.
