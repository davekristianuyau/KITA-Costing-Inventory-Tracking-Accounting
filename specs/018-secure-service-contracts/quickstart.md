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
./gradlew :workflow-service:test        # consumer-contract + coverage-guard tests (no Docker needed)
```
Expected: 100 tests, 96 green (the 4 Testcontainers ITs need Docker — house pattern). Then prove the
guard bites — all three were verified during implementation:

| Simulated drift | Observed result |
|---|---|
| Receiver renames `customerRef` in `SalesDtos` | contract test **stops compiling** (it reads the real record) |
| Adapter regresses to sending `customerId` | `Unrecognized field "customerId" … does not bind to SalesOrderCreateRequest` |
| New `*Port` method with no contract test | `unverified orchestrated call(s): [CrmPort#archiveCustomer]` |

## 2. Governed actions take real effect (US1 — SC-001/SC-002)
Bring the stack up **with encryption on and no manual certificate steps** (SC-008) — the `certs` init
container generates the CA + every bundle before anything else starts:
```bash
docker compose -f docker-compose.yml -f docker-compose.mtls.yml up --build -d
GATEWAY=http://localhost:8081 bash specs/018-secure-service-contracts/e2e/run-governed-actions.sh
```
For the FR-004/SC-001 surface proper, run the same script against the **Floci AWS-imitation deployment**
(production parity). ⚠️ **Prerequisite**: `workflow.hr.position-roles` must be configured, or every
governed action is refused — hr-service holds no back-office roles (see tasks.md "Open decision").
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

---

## Verified run — 2026-07-27 (local Docker, mtls overlay)

Everything below was executed, not inferred.

| Criterion | Evidence |
|---|---|
| **SC-001/002** | `run-governed-actions.sh` → **17/17 PASS, 0 fail**, incl. maker-checker receiving (PO → `RECEIVED`) and a build via real BOM explosion. Records confirmed in crm/procurement/operations/workflow schemas. |
| **SC-003** | Three drift simulations each failed the build (receiver rename → won't compile; stale caller key → `Unrecognized field "customerId"`; new port method → `unverified orchestrated call(s)`). |
| **SC-004** | Plaintext HTTP → 400; internal calls only over TLS. Every service→Postgres connection `TLSv1.3 / TLS_AES_256_GCM_SHA384`; Redis TLS-only (`port 0`, plaintext "Connection reset by peer"). |
| **SC-005** | No cert → 401; CA-signed but non-allowlisted CN → 401; **both persisted** in `service_call_refusal` with `attempted_cn`. |
| **SC-006** | 240-call mTLS loop across a live cert swap → **0 failures**; new serial served afterwards (no restart). |
| **SC-007** | `gradlew build`: only the pre-existing hr `OpenApiContractTest` fails (known-red on `main`, untouched here). Audit outcomes unchanged. |
| **SC-008** | `docker compose -f docker-compose.yml -f docker-compose.mtls.yml up --build -d` → all 8 containers healthy, **no manual certificate steps**. |

Prerequisite for governed actions: `workflow.hr.position-roles` must map the acting employee's HR
position (see the "Open decision" note in tasks.md). The dev stack ships a `back-office` mapping.
