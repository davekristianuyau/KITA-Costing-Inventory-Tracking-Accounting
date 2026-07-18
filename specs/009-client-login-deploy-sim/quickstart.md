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

- Open the frontend, log in as **client-a**'s user → land in the app; the data shown comes from **client-a's**
  backend only.
- Log in (separately) as **client-b**'s user → land in **client-b's** backend; different, isolated data. (US1/US2)

```bash
# API-level checks through the edge:
TOKEN_A=$(curl -s localhost:8080/auth/login -d '{"username":"...","password":"...","client":"client-a"}' -H 'content-type: application/json' | jq -r .token)
curl -s localhost:8080/api/operations/items -H "Authorization: Bearer $TOKEN_A"   # → client-a data
# a client-a token aimed at client-b's data must NOT succeed (isolation is by claim, not by URL param):
```

**Expect**: correct client data for each; **0** cross-tenant access; invalid credentials → 401. (SC-002/003)

## 3. Failure & session behaviors

- Wrong password → generic error, no redirect. Locked account (many tries) → 423. (FR-008/009)
- Stop a client's backend → that client's users see a clear "temporarily unavailable" state. (SC-006)
- Let a session expire / sign out → redirected to `/login`; old token no longer works. (FR-007/008)

## 4. AWS deployment imitation (LocalStack) — US4

```bash
bash sim/aws-imitation/deploy.sh client-a     # tflocal applies the 001 AWS module against LocalStack (no real cloud)
```

**Expect**: the client-a AWS deployment path runs end-to-end against LocalStack using **0** real cloud
credentials; the login flow reaches client-a's locally-imitated deployment. (SC-005)

## 5. Teardown

```bash
bash sim/sim-down.sh          # removes each client's containers + data independently, plus edge/identity/localstack
```

(FR-017)

---

Details: [contracts/](./contracts/) and [data-model.md](./data-model.md). Implementation steps → `/speckit-tasks`.
