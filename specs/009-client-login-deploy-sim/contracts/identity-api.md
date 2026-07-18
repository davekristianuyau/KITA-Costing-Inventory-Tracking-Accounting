# Contract — Identity Service API

Authenticates users and issues session tokens. Reached only through the edge (`/auth/**`). Holds no client
business data.

## `POST /auth/login`
Request: `{ "company": "<client identifier>", "username": "...", "password": "..." }` — company + username
resolve exactly one user (usernames are unique within a client, FR-019).
- **200** → sets an **httpOnly, Secure cookie** holding the **encrypted** session token (JWE), and returns
  `{ "client": "<tenant id>", "expiresIn": 5400 }`. The token is **asymmetrically signed** (verified with the
  public key) and valid for **90 minutes** (see data-model.md).
- **401** invalid credentials / unknown user — clear, **non-revealing** message; no token; no backend contacted.
- **423** account locked (too many failed attempts, FR-009).
- **400** malformed request.

Passwords are BCrypt-verified; never stored/returned/logged in plaintext (FR-010, SC-007).

## `POST /auth/logout`
Auth: `Authorization: Bearer <jwt>`. **204** → session invalidated (`jti` revoked / short TTL lets it lapse);
subsequent protected calls fail until re-login (FR-007).

## `GET /auth/validate` (internal, edge→identity or local verify)
Verifies a token's signature + expiry + revocation. **200** with claims, or **401**. (The edge MAY verify the
signature locally without this call; endpoint exists for revocation checks.)

## Health
`GET /actuator/health` → `200 {"status":"UP"}`.

## Acceptance
- Valid creds → 200 + JWT whose `client` matches the user's client. (FR-002/003)
- Invalid/unknown → 401, no token. Locked → 423. (FR-008/009)
- No response or log ever contains a plaintext password. (SC-007)
