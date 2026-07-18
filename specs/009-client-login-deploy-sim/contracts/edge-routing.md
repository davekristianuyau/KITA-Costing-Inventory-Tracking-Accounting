# Contract — Edge Gateway Routing & Isolation

The single public API edge. Routes by JWT `client` claim and enforces tenant isolation.

## Routes
- `/auth/**` → `identity-service` (unauthenticated; login/logout).
- `/api/**` → the caller's **client backend gateway**, selected by the validated JWT `client` claim.
- `/actuator/health` → edge health.

## Auth filter (on `/api/**`)
1. Read the session from the **httpOnly cookie** (decrypt the JWE); else **401**.
2. Verify the **asymmetric signature** (public key) + `exp` (90-min lifetime) + revocation; else **401**
   (expired/invalid → client returns to login). No shared secret — the edge cannot mint tokens either.
3. Read `client` claim → resolve the target backend (`<client>-gateway:8081`); unknown/inactive client → **401**.
4. **Strip** any inbound `X-Kita-*` headers; set trusted `X-Kita-User`, `X-Kita-Client` (+ `X-Kita-Roles`) from
   the token. (Prevents header spoofing.)
5. Proxy to the target backend.

## Isolation rules (SC-002, FR-005/006)
- A request is routed **only** to the backend named by its own token's `client` claim — never another.
- Each client backend gateway is configured with its `client` id and **rejects** a token whose `client` differs
  (defense-in-depth: even a mis-routed or replayed token is refused).
- If the resolved client backend is unreachable → **503** temporarily-unavailable (clear state, no fallback to
  another backend) (FR-016, SC-006).

## Acceptance
- Client-A user's `/api/...` reaches only client-A's backend; client-B's is never contacted. (SC-002)
- A client-A token presented for client-B's backend is rejected. (FR-006)
- Missing/expired/invalid token → 401; unreachable client backend → 503. (FR-008/016)
- Downstream services receive trusted `X-Kita-*` derived from the token, not from the client.
