# Quickstart & Validation: 018 Secure Service Contracts

Runnable validation that the two goals hold: **governed actions actually reach the real services**
(US1/US2) and **internal traffic is encrypted + mutually authenticated** (US3/US4). Details live in
[research.md](./research.md), [contracts/orchestrated-calls.md](./contracts/orchestrated-calls.md), and
[contracts/transport-security.md](./contracts/transport-security.md).

## Prerequisites
- Docker Desktop (for the composed stack / Testcontainers ITs — house pattern: contract + e2e run in CI;
  pure unit + adapter tests run locally).
- JDK 17, Gradle wrapper (from `backend/`). See [[kita-backend-service-conventions]].

## 1. Contract verification fails on drift (US2 — SC-003)
```bash
cd backend
./gradlew :workflow-service:test        # consumer-contract + coverage-guard tests
```
Expected: each `*Port` method's request/response binds + Bean-Validates against the receiver's **real**
DTO; the coverage guard lists every method. Then prove it bites: rename a field in any receiver request
record (or an adapter body key) → re-run → the matching contract test **fails and names the field**.
Revert.

## 2. Governed actions take real effect (US1 — SC-001/SC-002)
Run against the **Floci AWS-imitation deployment** (production-parity — the FR-004/SC-001 surface):
```bash
# bring up the Floci AWS-imitation stack (sim/aws-imitation/), encryption on, no manual cert steps (SC-008)
# then run the e2e action script (raise PO, record delivery, take sales order, build, maintain customer/supplier)
bash specs/018-secure-service-contracts/e2e/run-governed-actions.sh   # (added during US1)
```
Expected for each action: 2xx from `workflow-service`, and the record is visible in the **owning**
service with matching identifiers and amounts —
- PO + lines/qty/price in procurement (`GET /api/procurement/purchase-orders/{id}`);
- received qty reflected in operations stock (`GET /api/operations/items/{id}/availability`);
- sales order in operations with the submitted lines.
An invalid input returns the receiver's **actual** reason (FR-003), never a generic rejection.

## 3. Traffic is encrypted + mutually authenticated (US3 — SC-004/SC-005)
- Capture traffic between two service containers → **no** readable business data or `X-Kita-User`; the
  same for a service↔Postgres and service↔Redis connection (datastore TLS, Decision 6).
- Call a service from a client with **no / untrusted / expired** cert, or a valid cert whose CN is not
  allowlisted → **refused** (401/403) **and a persisted `service_call_refusal` row appears** in the
  receiving service's schema:
  ```sql
  SELECT occurred_at, peer_address, attempted_cn, reason, request_path FROM service_call_refusal;
  ```
  A genuine peer over mTLS behaves exactly as before (SC-007).

## 4. Rotation without downtime (US4 — SC-006)
```bash
# with the stack running and a steady call loop against a governed action:
# replace a service's mounted cert files with freshly issued ones
```
Expected: **zero** failed calls across the swap (Boot reloads the SSL bundle in place); each bundle's
remaining validity is visible via `GET /actuator/health` (ssl indicator) before expiry.

## 5. Regression: no behaviour change (FR-010 — SC-007)
```bash
cd backend && ./gradlew :workflow-service:build   # all existing behaviour/contract tests pass with TLS on
```
Expected: the pre-existing maker-checker, error-taxonomy, and activity-audit tests pass unchanged —
encryption changed transport only.

## Success signals
- SC-001/002: every governed action succeeds e2e **on Floci** and is visible in the owning service; 0
  shape-mismatch failures.
- SC-003: a renamed field on either side fails the build.
- SC-004/005: nothing readable on any link (HTTP hops + Postgres/Redis); unverifiable callers refused +
  **persisted** as refusal records.
- SC-006: 0 failed calls during rotation.
- SC-007: existing behaviour tests green with encryption on.
- SC-008: `docker compose up` brings the system up encrypted with no manual cert steps.
