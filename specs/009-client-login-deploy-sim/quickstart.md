# Quickstart — Client Login & Per-Client Deployment Simulation

Validates the feature end-to-end on one machine with Docker. Run from the repo root on branch
`009-client-login-deploy-sim`. Two demo clients are seeded: **client-a** (cloud preference AWS) and
**client-b** (e.g. GCP), each with a user.

## 1. Bring up the simulation (one command)

```bash
cp .env.example .env
bash sim/sim-up.sh            # builds/starts: 2 isolated client stacks (feature-008) + identity + edge + frontend
docker ps                     # each client stack + identity + edge + frontend should be healthy
```

Only the **frontend** is host-exposed (e.g. `http://localhost:8080`). Client backends, the edge, and identity
are private. (US3)

## 2. Log in as each client and prove isolation

- Open the frontend, log in with **company + username + password** as **client-a** (`alice`) → land in the app;
  the data comes from **client-a's** backend only.
- Log in (separately) as **client-b** (`bob`) → land in **client-b's** backend; isolated data. (US1/US2)

The session is an httpOnly, JWE-encrypted cookie (no token in page-readable storage); the edge routes `/api`
strictly by the token's `client` claim, so there is no URL/param to point at another tenant.

```bash
# API-level checks through the edge (the cookie carries the session — no Authorization header):
curl -s -c a.jar localhost:8080/auth/login -H 'content-type: application/json' \
  -d '{"company":"client-a","username":"alice","password":"demo-pass"}'   # → 200 + Set-Cookie
curl -s -b a.jar localhost:8080/api/operations/...                        # → routed to client-a only
# no cookie / a forged cookie → 401; the per-client gateway also rejects any client != its CLIENT_ID (403).

bash sim/sim-smoke.sh    # automated: health, only-frontend-exposed, both clients login→own backend
```

**Expect**: correct client data for each; **0** cross-tenant access; invalid credentials → 401. (SC-002/003)

## 3. Failure & session behaviors

- Wrong password → generic error, no redirect. Locked account (many tries) → 423. (FR-008/009)
- Stop a client's backend → that client's users see a clear "temporarily unavailable" state. (SC-006)
- Let a session expire / sign out → redirected to `/login`; old token no longer works. (FR-007/008)

## 4. AWS deployment imitation (Floci) — US4

```bash
bash sim/aws-imitation/deploy.sh client-a     # real Terraform (001 module) applied against Floci — no real cloud
bash sim/aws-imitation/verify.sh client-a     # resources exist in Floci; 0 real creds; login reaches the deployment
```

Floci is a drop-in [LocalStack replacement](https://floci.io/) (port 4566). Terraform + AWS CLI run in
containers, so no host `terraform`/`aws` is needed. The running feature-008 stack is the compute/DB stand-in.

**Expect**: the client-a AWS deployment path runs end-to-end against Floci using **0** real cloud
credentials; the login flow reaches client-a's locally-imitated deployment. (SC-005)

## 5. Teardown

```bash
bash sim/sim-down.sh          # removes each client's containers + data independently, plus edge/identity
docker compose -p kita-floci -f sim/aws-imitation/docker-compose.floci.yml down -v   # stop Floci
```

(FR-017)

---

Details: [contracts/](./contracts/) and [data-model.md](./data-model.md). Implementation steps → `/speckit-tasks`.
