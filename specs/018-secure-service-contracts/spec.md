# Feature Specification: Correct & Secure Service-to-Service Integration

**Feature Branch**: `018-secure-service-contracts`
**Created**: 2026-07-22
**Status**: Draft
**Input**: User description: "Fix the back-office service's calls to the other services so they actually work against the real services (today they send payloads those services reject), and make that internal service-to-service traffic secure — HTTPS/TLS instead of plaintext HTTP."

## Overview

The back-office workflow layer orchestrates the other services: it raises purchase orders, records and confirms
deliveries, takes sales orders, builds products, and maintains customer and supplier records by calling the
services that own that data. Two problems make that orchestration unfit for a real deployment:

1. **The calls do not match what the receiving services accept.** They were built against hand-written test
   doubles and never exercised against the real services, so field names and required fields drifted apart. Every
   affected action fails at the boundary and is reported to the user as an invalid request — a confusing,
   unactionable message for something that is not their fault.
2. **The calls travel in plaintext.** Internal traffic between services carries customer names, supplier terms,
   prices, quantities and the acting employee's identity. Anything able to observe the internal network sees all
   of it, and nothing proves a caller is really the service it claims to be.

This feature makes the orchestration **actually work** and makes it **secure in transit**, and adds the missing
verification that let the drift happen unnoticed: the contracts must be checked against the real services, not
against a copy of the caller's assumptions.

Discovered during feature 016 (workflow UI): with real services, every governed action is rejected at the
boundary; with test doubles, real record identifiers are unknown. Until this feature lands, no governed action
taken in the console has any real effect (016 SC-007).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Governed actions actually reach the owning services (Priority: P1) 🎯 MVP

A staff member performs a back-office action — raise a purchase order, record a delivery, take a sales order,
build a product, maintain a customer or supplier — and it takes effect in the service that owns those records.

**Why this priority**: Nothing else matters while every write fails at the boundary; this is the whole point of
the orchestration layer, and it unblocks 016's outstanding criterion.

**Independent Test**: For each governed action, perform it end to end against the real services and confirm the
record appears (or changes) in the owning service, with the same figures.

**Acceptance Scenarios**:

1. **Given** valid inputs referring to records that exist, **When** a staff member performs any governed action,
   **Then** it succeeds and the owning service holds the resulting record with matching identifiers and amounts.
2. **Given** a purchase order raised through the back office, **When** the purchasing records are viewed directly,
   **Then** the order, its lines, quantities and prices match exactly what was submitted.
3. **Given** a delivery confirmed through the back office, **When** inventory is viewed, **Then** stock reflects
   the received quantities.
4. **Given** an input the owning service considers invalid, **When** the action is attempted, **Then** the staff
   member sees a message naming the actual problem — never a generic rejection that hides the cause.

---

### User Story 2 - Integration is verified against the real services (Priority: P1)

Whoever changes either side of an integration finds out immediately if the two no longer agree, instead of it
surfacing months later in a running environment.

**Why this priority**: The drift existed because the only tests asserted the caller's own assumptions. Fixing the
calls without fixing the verification gap invites the identical failure back.

**Independent Test**: Change a field name on either side of an integration and confirm the automated checks fail.

**Acceptance Scenarios**:

1. **Given** the automated checks pass, **When** an action is exercised against the real services, **Then** it
   succeeds — the checks and reality agree.
2. **Given** either side changes a field name, a required field, or a type, **When** the checks run, **Then** they
   fail and name the mismatch.
3. **Given** a new orchestrated call is added, **When** the checks run, **Then** an unverified call is reported
   rather than silently trusted.

---

### User Story 3 - Internal traffic is encrypted and mutually authenticated (Priority: P2)

Traffic between services is encrypted, and each side can tell it is talking to a genuine peer, so an observer on
the internal network learns nothing and cannot pose as a service.

**Why this priority**: The data in flight includes customer, pricing and identity information. It follows US1
because securing a broken channel first would mean doing the work twice.

**Independent Test**: Observe internal traffic and confirm no readable business data; present an untrusted client
identity and confirm the call is refused.

**Acceptance Scenarios**:

1. **Given** two services communicating, **When** the traffic is observed, **Then** no business data or caller
   identity is readable.
2. **Given** a caller without a trusted service identity, **When** it calls a service, **Then** the call is
   refused and the attempt is recorded.
3. **Given** a service presents an expired or untrusted identity, **When** it calls, **Then** the call is refused
   with a clear operational error, distinct from a business rejection.
4. **Given** encryption is enabled, **When** actions run, **Then** they behave exactly as before — the change is
   transport-only, with no effect on outcomes or recorded activity.

---

### User Story 4 - Certificates can be rotated without an outage (Priority: P3)

An operator replaces the credentials that secure internal traffic, before they expire, without downtime.

**Why this priority**: TLS that cannot be rotated becomes an outage on a schedule; it is the operational half of
US3, but only after encryption exists.

**Acceptance Scenarios**:

1. **Given** running services, **When** their service credentials are rotated, **Then** calls keep succeeding
   throughout.
2. **Given** a credential nearing expiry, **When** it is inspected, **Then** the remaining validity is
   discoverable before it lapses.

### Edge Cases

- An owning service rejects a request for a genuine business reason (unknown supplier, inactive customer) → the
  staff member sees that specific reason, distinguished from a transport or contract failure.
- An owning service is unreachable or its certificate is invalid → reported as temporarily unavailable and
  retryable, never as the user's mistake.
- A required identifier the caller must supply (an order number, a customer code, a record type) is not something
  the staff member entered → the caller derives it deterministically, and the same action never produces two
  different records if retried.
- Local development must remain runnable without hand-managed certificates.
- Existing test doubles must stay usable for isolated service builds — the aim is that they cannot drift from the
  real contract unnoticed, not that they disappear.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every orchestrated call MUST use the request shape the receiving service actually accepts —
  matching field names, types, and required fields.
- **FR-002**: Every value the receiving service requires but the staff member does not supply MUST be derived
  deterministically by the caller, so a retry of the same action cannot create a second record.
- **FR-003**: A rejection by a receiving service MUST be surfaced with its actual reason, distinguishable from a
  transport failure and from a permission refusal.
- **FR-004**: Every governed back-office action MUST be demonstrated working end to end against the real services.
- **FR-005**: Automated checks MUST verify each integration against the **real** service contract, so a change on
  either side fails the build rather than surfacing at runtime.
- **FR-006**: Test doubles used for isolated builds MUST be held to the same contract as the real services, so
  they cannot silently diverge.
- **FR-007**: All service-to-service traffic MUST be encrypted in transit.
- **FR-008**: Each service MUST verify the identity of the service calling it and refuse callers it cannot
  verify; refusals MUST be recorded.
- **FR-009**: Service credentials MUST be rotatable without interrupting service, and their remaining validity
  MUST be observable before expiry.
- **FR-010**: Enabling encryption MUST NOT change any business outcome, authorization decision, or recorded
  activity — it is a transport change only.
- **FR-011**: Service credentials and private keys MUST NOT live in the repository, in configuration files, or in
  logs.
- **FR-012**: Local development MUST remain runnable end to end without operators hand-managing certificates.
- **FR-013**: The console's Workflow tab MUST need no change for governed actions to start taking real effect —
  this feature is entirely below the interface.

### Key Entities *(include if feature involves data)*

- **Orchestrated Call**: one back-office action's request to an owning service — its shape, required values, and
  the outcome it maps back to.
- **Integration Contract**: the agreement between a calling and a receiving service that the automated checks
  verify on both sides.
- **Service Identity**: the credential a service presents to prove which service it is, with an issuer and an
  expiry.
- **Derived Value**: a value the receiving service requires that the caller must produce deterministically (an
  order number, a record code, a record type) so retries stay idempotent.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: **100%** of governed back-office actions succeed end to end against the real services and are
  visible in the owning service — including 016 SC-007, which this unblocks.
- **SC-002**: **0** actions fail because of a caller/receiver shape mismatch.
- **SC-003**: A field renamed on either side of any integration causes an automated check to fail, in **every**
  integration, before it can reach a running environment.
- **SC-004**: **0** readable business data or caller identity is observable on internal traffic.
- **SC-005**: **100%** of internal calls from an unverifiable caller are refused and recorded.
- **SC-006**: Service credentials are rotated with **0** failed calls during the rotation.
- **SC-007**: Enabling encryption changes **no** business outcome — the existing behaviour checks pass unchanged.
- **SC-008**: A developer brings the whole system up locally, with encryption on, using the documented startup and
  **no** manual certificate steps.

## Assumptions

- The receiving services' current contracts are authoritative: where caller and receiver disagree, the **caller**
  is corrected — the owning service defines its own data.
- Where a receiving service requires a value no human supplies (an order number, a record code), deriving it in
  the caller is acceptable; introducing it into the user interface is not.
- The existing test doubles stay, for isolated builds; this feature binds them to the real contract.
- Encryption applies to traffic between internal services; how the public entry point terminates traffic from
  browsers is unchanged here.
- Local development may use locally generated credentials, provided nothing secret is committed.

## Dependencies

- **007 back-office workflows** — owns the calls being corrected.
- **003 / 005 / 006** — the services that own the records being written (inventory & sales, customers, suppliers).
- **004 hr** — consulted for the acting employee's roles; the same transport rules apply.
- **008 docker-cache-database** — the local composition these services run in.
- **016 workflow UI** — surfaces these actions; its SC-007 stays unmet until this ships.
- **017 account-employee identity** — independent; both are prerequisites for a real deployment of the back office.

## Out of Scope

- Any change to what the back office does, who may do it, or the maker-checker controls — corrections are to
  *how* calls are made, not to the rules.
- Changing the receiving services' own APIs to suit the caller.
- The browser-facing interface, including how the public entry point secures traffic from browsers.
- New orchestrated actions, or new fields exposed in the console.
- Certificate authority selection for production deployment (an infrastructure concern), beyond requiring that
  credentials be rotatable and never committed.
