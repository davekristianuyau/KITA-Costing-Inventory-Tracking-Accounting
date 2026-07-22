# Feature Specification: Account-to-Employee Identity

**Feature Branch**: `017-account-employee-identity`
**Created**: 2026-07-22
**Status**: Draft
**Input**: User description: "Account-to-employee identity mapping: give every login account its own employee identity end-to-end, so a signed-in user's back-office permissions come from their own employee record. Today identity accounts are standalone usernames while employee records live in the HR service with their own ids, and nothing connects them — so the back-office service cannot resolve a real signed-in user to an employee, and the local simulation falls back to a seeded directory keyed by login name. This feature closes that gap: an account carries the employee it belongs to, that identity travels with the session to the backend services, the HR employee record is the single source of truth for who someone is and what roles they hold, and joiners/leavers/role changes take effect without editing any seed data."

## Overview

A company's staff — cashiers, salespeople, stockmen, approvers — each sign in with their own account, and what they
are allowed to do in the back office must follow from **who they are as an employee**. Today those are two
unconnected worlds: a login account is a standalone username, while the employee record (with its status and roles)
lives in the personnel system under its own identifier. Nothing joins them, so a signed-in user cannot be resolved
to an employee at all; the local simulation papers over this with a hand-seeded directory keyed by login name, and
a real deployment simply could not authorize anyone.

This feature closes that gap. An account carries the employee it belongs to; that identity travels with the session
to every backend service; the employee record becomes the single source of truth for status and roles; and
joiners, leavers, and role changes take effect through normal personnel administration, with no seed data to edit.

It is a prerequisite for deploying the back-office (Workflow) capabilities for real. It is independent of the
Workflow UI work and is implemented after it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A signed-in user acts as their own employee (Priority: P1) 🎯 MVP

An employee signs in with their own account and performs a back-office action. The system resolves their account to
their employee record, reads that record's status and roles, and authorizes the action accordingly — with no seeded
directory and no identity supplied by the browser.

**Why this priority**: This is the whole point of the feature and the smallest end-to-end slice that removes the
test double from the authorization path. Everything else refines the lifecycle around it.

**Independent Test**: Link an account to an active employee holding a permitting role → sign in → perform an action
permitted by that role → it succeeds and is attributed to that employee. Sign in as an account linked to an
employee **without** that role → the action is refused as not permitted.

**Acceptance Scenarios**:

1. **Given** an account linked to an active employee, **When** the user performs a governed action, **Then** the
   action is authorized against that employee's roles and recorded as performed by that employee.
2. **Given** two accounts linked to two different employees, **When** each performs an action, **Then** each is
   attributed to its own employee — they are never conflated or substituted.
3. **Given** an employee's roles are changed in the personnel system, **When** they next act, **Then** the new
   roles apply without any redeployment, re-seeding, or re-login-with-special-setup.

---

### User Story 2 - Administer the account↔employee link (Priority: P2)

An administrator links an account to an employee, sees existing links, and unlinks when needed — so joiners get
access tied to their record and departures can be severed deliberately.

**Why this priority**: Without administration the link can only be created by whoever seeds the data, which is the
exact problem this feature exists to remove. It follows P1 because the resolution path must work first.

**Independent Test**: As an administrator, link a new account to an employee → that user can now act as them;
unlink → they can no longer perform governed actions, and the reason given says the account has no employee.

**Acceptance Scenarios**:

1. **Given** an unlinked account and an employee with no account, **When** an administrator links them, **Then**
   the link is recorded and takes effect on the user's next action.
2. **Given** a linked account, **When** an administrator unlinks it, **Then** governed actions are refused with a
   clear "no employee" reason, distinct from a permission refusal.
3. **Given** an employee already linked to an account, **When** an administrator tries to link a second account to
   the same employee (or a second employee to the same account), **Then** the attempt is refused with a clear
   reason.
4. **Given** any link or unlink, **When** it is made, **Then** who made it and when is recorded.

---

### User Story 3 - Leavers and inactive employees lose access immediately (Priority: P2)

When an employee is separated or made inactive in the personnel system, their account can no longer perform
back-office actions — without anyone having to remember to disable the login separately.

**Why this priority**: This is the control that makes the personnel record genuinely authoritative; it is the
difference between a convenience mapping and a security property.

**Independent Test**: Sign in as a linked employee, confirm an action succeeds → mark the employee separated →
retry the same action → it is refused, citing the employee's status, and the refusal is recorded.

**Acceptance Scenarios**:

1. **Given** an account whose employee is inactive or separated, **When** the user performs a governed action,
   **Then** it is refused with a reason naming the employee's status, distinct from "not permitted".
2. **Given** a separation takes effect while a user is already signed in, **When** they next perform an action,
   **Then** it is refused — an existing session does not extend access past separation.
3. **Given** a re-activated employee, **When** they act again, **Then** access resumes with the roles their record
   now holds.

---

### User Story 4 - The personnel record is the only source of roles (Priority: P3)

Back-office authorization reads status and roles from the real personnel system rather than any test double or
seeded list, in every environment where the feature is deployed.

**Why this priority**: The previous stories can be satisfied while a stand-in directory still lingers in some
environment; this story removes it and proves the real path is the one exercised.

**Independent Test**: With the stand-in directory removed, exercise the P1–P3 scenarios end to end and confirm all
still hold; confirm no environment resolves employees from seeded login names.

**Acceptance Scenarios**:

1. **Given** the feature is deployed, **When** any governed action is authorized, **Then** the employee status and
   roles used come from the personnel system of record.
2. **Given** the personnel system is temporarily unreachable, **When** a user performs a governed action, **Then**
   they are told the check is temporarily unavailable and may retry — access is never silently granted.
3. **Given** a role token exists in the personnel record that the back office does not recognize, **When**
   authorization runs, **Then** it grants nothing and does not error.

### Edge Cases

- An account exists with no employee linked → governed actions refused with a "no employee" reason, distinct from
  both "not permitted" and "employee inactive".
- The linked employee record no longer exists (deleted or moved) → refused with a clear reason; never treated as an
  all-access identity.
- A user signs in but only uses capabilities that need no employee identity → they continue to work normally; the
  link is required for governed back-office actions, not for signing in.
- Sessions issued before a link changed → the current link and current employee status govern the next action, not
  whatever was true at sign-in.
- The same person legitimately has two accounts, or an account is reassigned to a new hire → refused by the
  one-to-one rule with a clear reason; reassignment is an explicit unlink-then-link.
- Existing simulation logins that were resolving through the seeded directory → must be migrated to real links so
  no environment silently loses access when the stand-in is removed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every login account MUST be able to carry the identity of the employee it belongs to.
- **FR-002**: The account↔employee relationship MUST be one-to-one: an account maps to at most one employee, and an
  employee to at most one account. Violations are refused with a clear reason.
- **FR-003**: The signed-in user's employee identity MUST travel with their session to the backend services that
  need it, without the browser or client supplying, choosing, or being able to alter it.
- **FR-004**: Back-office authorization MUST resolve the acting employee from that identity and read the
  employee's **status and roles from the personnel system of record** — never from a seeded list, a test double, or
  anything the caller asserts.
- **FR-005**: An account with no linked employee MUST be refused for governed actions, with a reason distinguishable
  from a permission refusal and from an inactive-employee refusal.
- **FR-006**: An account whose employee is inactive, separated, or missing MUST be refused for governed actions,
  with a reason naming that cause, and the refusal MUST be recorded like any other attempt.
- **FR-007**: Role and status changes made in the personnel system MUST take effect for subsequent actions without
  redeployment, data re-seeding, or the user having to be re-provisioned.
- **FR-008**: Administrators MUST be able to link an account to an employee, view current links, and unlink.
- **FR-009**: Every link and unlink MUST record who performed it and when.
- **FR-010**: Administering links MUST itself be restricted to authorized administrators.
- **FR-011**: When the personnel system cannot be reached, governed actions MUST fail closed with a
  "temporarily unavailable" outcome — never granting access on a failed lookup.
- **FR-012**: The stand-in employee directory used by the simulation MUST be removed from every deployed path, and
  the simulation's existing logins migrated to real links so the demonstrable behaviour is the real behaviour.
- **FR-013**: The feature MUST NOT change what any role is allowed to do — the authorization rules themselves are
  out of scope; only *whose* roles are being checked changes.

### Key Entities *(include if feature involves data)*

- **Account**: a login identity (the thing a person signs in with). Gains an optional link to one Employee.
- **Employee**: the personnel record — the source of truth for a person's identity, employment status, and roles.
- **Account–Employee Link**: the one-to-one association between them, with the administrator and timestamp that
  created or removed it.
- **Session Identity**: what the signed-in user's session carries to backend services so the acting employee can be
  resolved; asserted by the platform, never by the client.
- **Resolution Outcome**: the result of resolving an account to an actable employee — resolved, no employee linked,
  employee inactive/separated/missing, or temporarily unavailable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A newly hired employee can be given working back-office access purely through normal administration
  (create the account, link it to their personnel record) — **zero** code changes, redeployments, or seed edits.
- **SC-002**: A separated employee loses the ability to perform any governed action on their **next attempt**,
  including from a session opened before the separation.
- **SC-003**: **100%** of governed actions are attributed to the acting user's own employee record; **0** actions
  are attributed to a shared, stand-in, or substituted identity.
- **SC-004**: The four resolution failures — no employee linked, employee inactive/separated, employee missing,
  personnel system unavailable — are each reported distinguishably, and none is conflated with "not permitted".
- **SC-005**: **0** deployed paths resolve an employee from a seeded login-name directory once this feature ships.
- **SC-006**: A role change in the personnel system is reflected in what the user can do on their next action,
  with no re-login required beyond normal session rules.
- **SC-007**: The existing back-office behaviours (authorization outcomes, maker-checker controls, recorded
  activity) continue to pass their existing checks unchanged — this feature changes *who* is resolved, not *what*
  the rules are.

## Assumptions

- Accounts and personnel records are administered by the business; this feature connects them rather than merging
  them into one system.
- Each client deployment is isolated, so links are scoped within a client — an account can only be linked to an
  employee of the same client.
- Not every account needs an employee link (service or administrative accounts may have none); such accounts simply
  cannot perform governed back-office actions.
- Signing in does not require a linked employee — the link gates governed back-office actions, not authentication.
- The authorization rules (which role may perform, make, or check which action) already exist and are unchanged
  here.
- Employee status and roles are already maintained in the personnel system; this feature does not introduce a new
  place to edit them.

## Dependencies

- The existing login/session platform that issues and validates sessions and forwards trusted identity.
- The personnel system that owns employee records, employment status, and role assignments.
- The back-office capability whose authorization consumes the resolved employee.
- **016 workflow-ui** — surfaces the behaviour this feature makes real; independent, and implemented before this.

## Out of Scope

- Changing what any role is permitted to do, or editing the authorization rules themselves.
- Self-service account registration, password/credential management, or single sign-on integration.
- Employee record management (hiring, separation, role assignment) — that stays in the personnel system.
- Multi-employee or delegated/"act on behalf of" identities — one account, one employee.
- Any user interface beyond what administering the link requires.
