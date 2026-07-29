# Contract: Internal Transport Security (018)

The transport all internal service-to-service calls must satisfy (US3/US4). This is **transport-only** —
no request body, authorization decision, or `back_office_activity` record changes (FR-010).

## Scope (clarified 2026-07-23 — "maximum security")

Encryption + mutual auth covers **all** internal HTTP hops: `workflow-service`→{operations,hr,crm,
procurement} **and** `gateway`→every backend service. Datastore connections (Postgres, Redis) are also
encrypted (see below). Only browser→gateway is out of scope.

## Encryption + mutual authentication (FR-007, FR-008)

- Every internal call is over **TLS**; each server trusts the dev **CA** and each client presents its own
  CA-signed cert (encrypted + peer-authenticated).
- **`client-auth: want`, not `need`** — the channel still requests + validates the client cert, but lets
  the request reach a mandatory **`ServiceIdentityFilter`** in the receiving service, which verifies the
  peer cert against the **service allowlist**. This is deliberate: it makes the *no-cert* refusal
  **recordable** (a `need` handshake would drop it before any app code — see research Decision 3).
- **Refuse + record** (FR-008): the filter refuses any caller it cannot verify — no cert, untrusted CA,
  expired, or CN not on the allowlist — returning 401/403 and **persisting a `service_call_refusal` row**
  (peer address, attempted CN, reason, path). Distinct from a business 422 and an actor-permission 403.

**Verification (SC-004/SC-005)**: observe internal traffic → no readable business data or caller
identity; present a client with no / untrusted / expired cert, or a valid cert with a non-allowlisted CN →
the call is refused **and a persisted refusal record appears**; a genuine peer over mTLS → identical
outcome to before (SC-007).

## Spring Boot wiring (3.5 SSL bundles)

- Server: `server.ssl.bundle=<svc>`, `server.ssl.client-auth=want`; bundle = the service's key material +
  the CA truststore. A `ServiceIdentityFilter` (all 5 backend services) enforces identity + records refusals.
- Client: the `RestClient.Builder` (workflow + gateway) is built from the same SSL bundle (its key for
  client-auth + CA truststore) so outbound calls present identity and trust the CA.
- Bundles: `spring.ssl.bundle.pem|jks.<svc>` pointing at the mounted cert files; **`reload-on-update: true`**.

## Datastore encryption (FR-007, Decision 6)

- **Postgres**: TLS on the server (dev-CA cert); every service's JDBC URL sets `sslmode=require` (local) /
  `verify-full` with the CA (production-parity / managed cloud, where the provider terminates TLS).
- **Redis** (operations-service only): Redis TLS port + server cert; Lettuce SSL on the client.
- Datastore certs come from the same bootstrap; never committed (FR-011).

## Rotation without downtime (FR-009, US4)

- Replacing the mounted cert files triggers Boot's SSL-bundle reload in place — no restart, no dropped
  calls (SC-006).
- Remaining validity is observable **before** expiry via `management.health.ssl` (certificate-expiry
  health indicator) plus an actuator/info contributor exposing each bundle's `notAfter`.

## Credential handling (FR-011)

- CA + per-service key material is **generated**, never committed — not in the repo, tfvars, or logs.
- Local: generated into a Docker volume / gitignored path by the cert-bootstrap step.
- Real deployments: sourced from the cloud secret store (per `{client}-{env}`, existing convention);
  CA selection for production is an infra concern (out of scope) beyond "rotatable + never committed".

## Local developer experience (FR-012, SC-008)

- `docker compose up` (and the Floci deploy) runs a **cert-bootstrap init** that generates the CA +
  bundles for all 5 services + the gateway + Postgres/Redis server certs into the shared volume before
  anything starts — the whole system comes up **with encryption on and no manual cert steps**.
- Pure unit / adapter tests use the `fake` adapters (no TLS). Standalone `bootRun` may disable mTLS via a
  profile; the **composed** stack (what SC-008 measures) always has it on.

## Scope boundaries

- Browser → gateway TLS termination is **unchanged** (out of scope).
- No new authorization or identity-of-*actor* semantics — mTLS proves the *service* identity; the actor's
  roles still come from HR / gateway role headers as today.
