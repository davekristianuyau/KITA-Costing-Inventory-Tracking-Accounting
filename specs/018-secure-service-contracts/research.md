# Research: Correct & Secure Service-to-Service Integration (018)

Phase 0 output. Resolves the technical unknowns behind the spec's two problems — **contract drift**
(US1/US2) and **plaintext, unauthenticated transport** (US3/US4) — grounded in the current code.

## Context established from the code

- 6 Spring Boot 3.5.0 services on a private `kita` bridge network; only `gateway` is host-exposed
  (`docker-compose.yml`). All internal calls are plaintext `http://` (base-urls in
  `workflow-service/src/main/resources/application.yml`).
- The orchestration layer is `workflow-service`. Its outbound calls live in
  `ports/http/Http{Operations,Procurement,Crm,Hr}Adapter.java`, all routed through
  `ports/http/RemoteCall.java` (retry + `X-Idempotency-Key` + `X-Kita-User` forward + 409-as-applied).
- The `http` adapters are `@ConditionalOnProperty(workflow.<svc>.adapter=http)`; the default is `fake`
  (in-memory). They were built against those fakes and **never exercised against the real services** —
  the root cause of the drift.

## Decision 1 — Correct the caller to the receivers' real contracts (US1, FR-001/002/003)

**Decision**: The receiver DTOs are authoritative (spec Assumption). Rewrite each `Http*Adapter` request
and response mapping to the shapes the controllers actually accept/return, deriving in the caller every
value a human does not supply. Confirmed drift (audit completed as first US1 task via the Decision-2
harness — these are the known ones):

| Call | Adapter sends / reads | Receiver actually expects / returns | Fix |
|------|----------------------|-------------------------------------|-----|
| Create sales order | `POST /sales-orders {customerId}`, then `POST /sales-orders/{id}/items` per line | `POST /sales-orders {customerRef, lines:[{itemId(UUID),quantity,uom,unitPrice}]}` — **no `/items` endpoint**; lines are atomic at create | Build one create-with-lines body; drop `addSalesOrderLine`; field `customerRef`; resolve item ref→**UUID** |
| Sales order response | reads `salesOrderId` | returns `{id, customerRef, status, lines[]}` | read `id` |
| Build | `POST /builds {itemId, quantity}` | `POST /builds {finishedItemId(UUID), locationId(UUID), quantity}` | resolve `finishedItemId`; **derive `locationId`** (default location) |
| Create PO | `POST /purchase-orders {supplierId(String), lines:[{itemId,quantity,unitCost}]}`, reads `purchaseOrderId` | `{poNo?, supplierId(UUID), origin?, lines:[{itemRef, qtyOrdered, agreedPrice}]}`; returns `{id,…}` | `supplierId` UUID; line `itemRef/qtyOrdered/agreedPrice`; read `id`; `poNo` left null (receiver derives) |
| Receive | `POST …/receipts {lines:[{itemId, quantityReceived}]}`, reads `receiptId,poStatus` | `{lines:[{itemRef, qtyReceived}]}`; returns receipt response `{id, …status}` | line `itemRef/qtyReceived`; read real id/status keys |
| Create supplier | `POST /suppliers {name, active}`, reads `supplierId` | `{supplierCode, name, email?, phone?, …}`; returns `{id,…}` | **derive `supplierCode`**; read `id` |
| Set supplied items | `PUT /suppliers/{id}/items {items:[…]}` | `POST /suppliers/{id}/items` **single** `SupplierItemRequest{itemRef, supplierPrice, leadTimeDays?, minOrderQty?}` | POST per item; field names |
| Create customer | `POST /customers {name, active}`, reads `customerId` | `CreateCustomerRequest{…}`; returns `CustomerResponse{id,…}` | match request; read `id` |

**FR-002 derived values** (deterministic, so a retry cannot create a second record; combined with the
existing `X-Idempotency-Key` + 409-as-applied): `locationId` (default/primary location via
`GET /locations`), item ref→UUID (via `GET /items` for operations, which speaks UUIDs; procurement
speaks `itemRef` strings so no resolution there), `supplierCode` (deterministic slug of the name), `poNo`
(left null — the receiver generates it).

**FR-003 error taxonomy**: `RemoteCall.applied` currently collapses every non-5xx 4xx to
`ValidationException("downstream rejected the request: " + status)` — it discards the receiver's reason.
Fix: parse the receiver's error body (its `ProblemDetail`/message) and surface it, keep the three
outcomes distinct — business rejection → 422 with the real reason; transport/cert/handshake failure →
503 retryable (`TransientDownstreamException`); permission refusal (receiver 403) → surfaced as
NOT_PERMITTED, not as invalid-input.

**Rationale**: The owning service defines its own data (spec); correcting the caller is the smallest change
that makes every governed action work. **Alternatives rejected**: changing receiver APIs to match the
caller (spec Out-of-Scope, would ripple to specs 003/005/006); a translation layer in the gateway
(hides the contract further, adds a hop).

## Decision 2 — Verify each integration against the *real* contract in the build (US2, FR-005/006)

**Decision**: A lightweight **consumer-contract test** in `workflow-service`, no running stack:
- Add each receiver's request/response DTO records as a `testImplementation` project dependency
  (monorepo Gradle — the receivers are sibling modules).
- Per port method, a test builds the exact body the adapter serialises (same Jackson `ObjectMapper`) and
  **binds + Bean-Validates** it against the receiver's real request record. A renamed / missing /
  re-typed field fails binding or validation → build red (SC-003). Response mapping is checked the same
  way against the receiver's real response record.
- A **coverage/registry guard test** enumerates every `*Port` method and asserts each has a contract
  test — an added-but-unverified call fails the guard (FR-005 scenario 3).
- The `InMemory*Adapter` fakes are held to the same DTO contract (FR-006) via a shared shape test, so a
  fake cannot drift from the real receiver unnoticed.
- End-to-end proof (FR-004, US1 Independent Test — clarified 2026-07-23): performed against the **Floci
  AWS-imitation deployment** (the production-parity emulator that stands up the services as they run on
  AWS — `sim/aws-imitation/`), so a pass reflects real AWS-deploy behaviour; no live cloud account needed.
  Each governed action is performed and the record asserted present in the owning service. Captured in
  `quickstart.md`. (Pure adapter/contract tests still run locally without a stack — house pattern.)

**Rationale**: Binding against the receiver's own DTO *is* verifying the real contract (same records the
controllers use), and it fails the build on either-side drift — the exact gap that let the drift happen.
Fits Constitution VI (simplicity). **Alternatives rejected**: **Spring Cloud Contract** (provider stubs,
DSL, plugin — more moving parts than a monorepo needs, Constitution VI); a shared DTO module extracted
from every service (large refactor across 003/005/006, out of scope); relying only on a full e2e stack
(too slow/coarse to name the mismatched field, and Docker-off locally).

## Decision 3 — mTLS on *every* internal HTTP hop, identity enforced + refusals persisted at the app layer (US3, FR-007/008/010/011)

**Scope (clarified 2026-07-23 — "maximum security")**: mTLS covers **all** service-to-service HTTP —
`workflow-service`→{operations,hr,crm,procurement} **and** `gateway`→every backend service. Each of the 5
backend services is both a server (receives) and, for `workflow-service`/`gateway`, a client (calls).
(Datastore encryption is Decision 6.)

**Decision**: **Application-level TLS with mutual auth**, using **Spring Boot SSL bundles** (3.5), with
**identity verification and refusal-recording moved into an application filter** rather than left purely at
the TLS handshake:
- A dev **CA** signs one server+client cert per service (SAN = docker service name). Each service:
  `server.ssl` from a PKCS12/PEM bundle + truststore = the CA; the client side (`workflow`/`gateway`
  `RestClient`) is built from the same bundle so it presents its identity and trusts the CA — traffic is
  encrypted (FR-007) and each side authenticates the peer (FR-008).
- **`client-auth: want`, not `need`** — deliberately. With `need`, a no-cert / bad-CA caller is dropped at
  the TLS handshake **before any application code runs**, so it **cannot be persisted** — and FR-008
  (clarified) now requires a persisted refusal record. `want` keeps the channel encrypted and still
  requests the client cert, but lets the request reach a mandatory `ServiceIdentityFilter` that: reads the
  validated peer cert (`jakarta.servlet.request.X509Certificate`), checks its CN against the **service
  allowlist**, and — for any caller it cannot verify (no cert, untrusted CA, expired, or CN not on the
  allowlist) — **writes a Refusal Record and returns 401/403**. So every unverified caller is both refused
  and recorded, and a genuine peer is indistinguishable in outcome from `need`.
- **Refuse + record (FR-008)**: enforced in the filter; the persisted `service_call_refusal` row captures
  peer address, attempted CN, reason (NO_CERT / UNTRUSTED / EXPIRED / NOT_ALLOWLISTED), timestamp — see
  [data-model.md](./data-model.md). This is a **transport/identity** refusal, kept distinct from a business
  422 and an actor-permission 403.
- **Transport-only (FR-010)**: no change to bodies, actor authorization, or the `back_office_activity`
  audit — verified by the existing behaviour/contract tests passing unchanged with TLS on (SC-007).
- **No secrets in the repo (FR-011)**: certs/keys generated into a Docker volume / gitignored dir,
  injected by env/mount, never committed.

**Rationale**: No service mesh / k8s in this stack, so a sidecar mesh (Istio/Linkerd/SPIFFE) is
disproportionate (Constitution VI). SSL bundles are the idiomatic Boot-3.5 mechanism and enable Decision 4.
Choosing `want` + app-layer enforcement is what makes FR-008's *persisted* refusal achievable for the most
likely probe (a caller with no cert) — pure `need` would satisfy encryption but silently drop exactly the
attempts the user wants recorded. **Alternatives rejected**: `client-auth: need` with only handshake logs
(cannot persist no-cert refusals — fails the clarified FR-008; a container-level handshake hook to reach
the DB is far more fragile than a servlet filter); service mesh (operational weight, no orchestrator);
gateway-only TLS with plaintext behind it (readable internal traffic — fails SC-004).

## Decision 4 — Rotation without downtime + observable validity (US4, FR-009)

**Decision**: SSL bundles with **`reload-on-update: true`** — Boot watches the mounted cert files and
reloads the SSL context in place, so replacing the files rotates credentials without a restart or dropped
calls (SC-006). Validity is observable via the built-in **`management.health.ssl`** certificate-expiry
health indicator (+ an `info`/actuator contributor exposing each bundle's `notAfter`), so remaining
validity is discoverable before expiry.

**Rationale**: Built into the mechanism chosen in Decision 3 — no extra dependency. **Alternatives
rejected**: restart-to-rotate (an outage on a schedule — the anti-goal in the story); an external
cert-manager/Vault agent (infra concern, out of scope; the spec only requires rotatable + never
committed).

## Decision 5 — Cert-free local developer experience (FR-012, SC-008)

**Decision**: The `docker-compose` stack runs a **cert-bootstrap init step** that generates the dev CA +
**per-service bundles for all 5 services + the gateway, plus server certs for Postgres and Redis** (see
Decision 6) into the shared volume before anything starts — so `docker compose up` (and the Floci deploy)
brings the whole system up **with encryption on and zero manual cert steps**. Pure unit / adapter tests
keep using the `fake` adapters (no TLS), and standalone `bootRun` outside the compose can leave mTLS off
via a profile — the composed/Floci stack, which SC-008 measures, always has it on.

**Rationale**: Satisfies SC-008 (encryption on, no manual steps) and FR-012 (no hand-managed certs)
together, without weakening the composed posture. **Alternatives rejected**: committing dev certs
(violates FR-011); requiring developers to run an openssl script by hand (fails SC-008); disabling
encryption locally (fails SC-008, and would mean the encrypted path is never exercised in dev).

## Decision 6 — Encrypt the datastore connections too (FR-007, clarified "maximum security")

**Decision**: Extend encryption to the **Postgres and Redis** connections (the clarified scope), not just
the HTTP hops:
- **Postgres**: enable TLS on the Postgres container (server cert from the same dev CA) and set every
  service's JDBC URL to `sslmode=require` (dev) / `verify-full` with the CA (production-parity). Managed
  cloud Postgres (RDS / Cloud SQL / Azure Flexible) already terminates TLS — there the change is
  `sslmode` on the client, no server config.
- **Redis** (only `operations-service` uses it): enable Redis TLS (`--tls-port`, server cert) and Lettuce
  SSL on the client.
- Certs generated by the Decision-5 bootstrap; never committed (FR-011).

**Rationale**: The clarification chose maximum security, and business data (prices, customer names) also
travels service→datastore. Postgres `sslmode` + Redis TLS are built-in, no new dependency. **Alternatives
rejected**: leaving datastore links plaintext (contradicts the clarified FR-007/SC-004); a full
verify-full CA chain in local dev (heavier than needed locally — `require` locally, `verify-full` in the
production-parity/cloud config).

## Cross-cutting notes

- **Receiver-side authorization**: receivers run their own `caller.require(Role.…)`. In the local
  composed demo they run in security-stub mode (all roles granted — the 015 pattern), so forwarding
  `X-Kita-User` suffices; in a real deployment the gateway injects role headers. mTLS proves the *service*
  identity; it does not replace the actor's role headers. No change to who-may-do-what (spec Out-of-Scope).
- **No NEEDS CLARIFICATION remain** — the spec is precise and the code is grounded above.
