# Implementation Plan: Account-to-Employee Identity

**Branch**: `017-account-employee-identity` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-account-employee-identity/spec.md`

## Summary

Nothing connects a login account to an employee record: `edge-gateway` sets `X-Kita-User` to the
**username**, while `workflow-service` looks employees up by **HR UUID**. The demo only works because
`DemoSeeder` names logins after the in-memory fake's employee ids (`emp-sales`…). This feature makes the
personnel record the real source of identity: **hr-service** gains the account link *and* the back-office
roles it never had, workflow resolves the acting employee by account username, and both stand-ins are
retired. The **session token is unchanged** and the edge already strips inbound `X-Kita-*` and asserts the
identity, so FR-003 holds as-is; `edge-gateway` changes only where it *gets* roles. See
[research.md](./research.md).

> **Spec correction (Phase 0)**: the spec assumed *"status and roles are already maintained in the
> personnel system."* Status yes; **roles no** — hr-service stores none, and its own `Role` enum governs
> HR's own API. FR-004 is unachievable without adding role storage, so that is in scope here.
>
> **Clarifications of 2026-07-29 widened this feature** (FR-014…FR-020). Beyond the link, 017 now also:
> stores roles in hr with an **`OWNER`** superuser role (Decisions 2, 6); makes account names **permanent**
> so a rename cannot transfer an identity (Decision 3b); **resolves session roles per request at the edge
> for every service and retires the permissive `stub` fallback** (Decision 7 — the largest addition); and
> lets an `OWNER` be **both maker and checker**, recorded distinguishably (Decision 8).

## Technical Context

**Language/Version**: Java 17; Spring Boot 3.5.0 (Gradle wrapper 8.10.2)
**Primary Dependencies**: Spring Web / Data JPA / Validation, Flyway, Jakarta Bean Validation; the existing
`RemoteCall` + `contractTest` harness (018) on the caller side
**Storage**: PostgreSQL, schema-per-service. **hr-service only**: 1 new column (`employee.account_username`)
+ 2 new tables (`employee_role`, `identity_change`) in a single Flyway `V11`. No other service gains schema.
**Testing**: JUnit 5 + AssertJ pure unit tests (local); Testcontainers ITs + `*ApiContractTest` (Docker);
workflow's `contractTest` source set binds to hr's **real** DTOs, so this change is drift-checked by construction
**Target Platform**: Linux containers — composed stack + Floci; one isolated deployment per client
**Project Type**: Backend microservices (monorepo `backend/`); no frontend change
**Performance Goals**: one HR round trip per governed action (already the case for status); **deliberately
uncached** — caching would reintroduce the revocation delay this design exists to remove
**Constraints**: FR-003 already satisfied (edge strips + asserts); FR-011 **fail closed** when HR is
unreachable; FR-013 authorization rules unchanged **except the deliberate FR-020 `OWNER` exemption**; the four
resolution failures must stay distinguishable (SC-004); roles are **never** written into the session token
**Scale/Scope**: hr-service (link + roles + `OWNER` + admin + audit); workflow-service (resolve by username,
outcome taxonomy, `OWNER` self-review exemption, `OWNER` short-circuit in `ActionAuthorizer`, delete `HrPositionRoles`);
**edge-gateway** (resolve roles per request, fail closed); **hr/crm/procurement `CallerContext`**
(`OWNER` implies-all; absent header grants nothing); identity-service (permanent account names, real `DemoSeeder` links); compose/sim (`HR_ADAPTER=http`,
`stub` off)

## Constitution Check

*GATE: must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| I. Specification-Driven | ✅ Spec → plan → artifacts, prioritized US1–US4. Phase 0 corrected a false spec assumption rather than building on it. |
| II. TDD (NON-NEGOTIABLE) | ✅ Each slice is test-first: the resolution-outcome taxonomy and the one-to-one rule are pure-unit testable; `contractTest` goes red the moment hr's DTO changes, driving the caller fix. |
| III. Security & Data Integrity (NON-NEGOTIABLE) | ⚠️→✅ **This is a security feature** and net strongly positive: roles are server-resolved per request (never carried in the token), revocation is immediate, unreachable HR fails **closed**, the permissive `stub` fallback is retired, account names are permanent so an identity cannot transfer, and every link/role change is audited under `OWNER`. **One deliberate weakening**: FR-020 lets an `OWNER` satisfy both halves of a maker-checker control — see Complexity Tracking. |
| IV. Environment Isolation | ✅ One deployment per client; hr has no tenant concept, so a link cannot span clients. No prod data in dev. |
| V. Observability | ✅ Four distinguishable outcomes, each recorded in `back_office_activity`; every link/role change records who + when. |
| VI. Simplicity & YAGNI | ✅ Chose the option leaving identity/token/edge **untouched**; one migration, no new module, no new header, no cache. |
| VII. Automated Quality Gates | ✅ `:hr-service:build` + `:workflow-service:build` (incl. `contractTest`) gate merges; 017 should also **fix** hr's long-standing `OpenApiContractTest` red by documenting the new endpoints. |

**Result: PASS** — with one recorded, user-accepted trade-off (below).

## Project Structure

### Documentation (this feature)
```text
specs/017-account-employee-identity/
├── plan.md              # this file
├── research.md          # Phase 0 — 5 decisions + what the code actually does
├── data-model.md        # Phase 1 — Employee(+link), EmployeeRole, IdentityChange, Resolution Outcome
├── quickstart.md        # Phase 1 — validation for SC-001…SC-007
├── contracts/hr-identity-api.md
├── checklists/requirements.md
└── tasks.md             # /speckit.tasks — NOT created here
```

### Source Code (repository root)
```text
backend/hr-service/                                    # owns the link + the roles
├── src/main/java/com/kita/hr/employee/
│   ├── Employee.java                                  #   + accountUsername (unique, nullable)
│   ├── EmployeeRole.java + repository                 #   NEW — back-office role tokens (opaque)
│   ├── IdentityChange.java + repository            #   NEW — link/role audit (who, when)
│   └── EmployeeResponse.java                          #   + accountUsername, roles  ← contract change
├── src/main/java/com/kita/hr/api/
│   ├── EmployeeController.java                        #   + by-account lookup, link/unlink, roles
│   └── AccountLinkController.java                     #   NEW — GET /api/hr/account-links
└── src/main/resources/db/migration/V11__account_link_and_roles.sql

backend/workflow-service/                              # resolves by account username
├── src/main/java/com/kita/workflow/ports/HrPort.java  #   Optional → ResolutionOutcome
├── .../ports/http/HttpHrAdapter.java                  #   by-account lookup; DELETE HrPositionRoles
├── .../ports/fake/InMemoryHrAdapter.java              #   test seam only, held to the contract
├── .../actor/ActorResolver.java                       #   4 distinct outcomes, fail-closed
└── src/contractTest/java/.../HrEmployeeContractTest.java

backend/edge-gateway/.../SessionAuthFilter.java         # resolve roles from hr PER REQUEST, fail closed
backend/{hr,crm,procurement}-service/
└── .../common/security/CallerContext.java             # OWNER implies-all; absent header ⇒ NO roles (stub off)
backend/workflow-service/.../authorization/ActionAuthorizer.java  # OWNER short-circuit — NOT its CallerContext,
                                                        # which by design does not read roles at all
backend/workflow-service/.../actor/BackOfficePipeline.java  # skip self-review guard when actor holds OWNER

backend/identity-service/.../config/DemoSeeder.java    # real employees + links (+ an OWNER), not emp-* names
backend/identity-service/.../domain/AppUser.java       # account names permanent, never reissued
docker-compose.yml, sim/                               # HR_ADAPTER: fake → http; stub off (016's note names 017)
```

**Structure Decision**: hr-service takes the new state and endpoints; workflow-service changes how it
resolves the actor; **edge-gateway becomes the single per-request role-resolution point**, which is what
lets all four services keep their existing `CallerContext` and change only their absent-header behaviour.
The **session token is still untouched** — deliberately, since putting roles in it would break SC-002/SC-006.

## Phasing (maps to user stories, MVP-first)

- **US1 (P1, MVP)** — link + roles (+ `OWNER`) in hr, resolution by username in workflow with the four-outcome
  taxonomy, activity attributed to the real employee. The smallest slice that removes the test double from
  the authorization path.
- **US2 (P2)** — administration under `OWNER`: link / list / unlink, one-to-one enforcement (409), role
  grant/revoke, full audit; permanent account names; the `OWNER` maker-checker exemption (FR-020) + the
  single-person-approval query (SC-010).
- **US3 (P2)** — leavers: non-`ACTIVE` refused with a status-naming reason; a live session doesn't extend access.
- **US4 (P3)** — **the widest slice, and the natural split point.** Retire both stand-ins (`HrPositionRoles`,
  `emp-*` seeded logins), flip `HR_ADAPTER=http`, move role resolution into the edge, turn `stub` off across
  all four services, and resolve the base-stack risk above. If this feature needs to ship sooner, US1–US3
  stand alone and **US4 can become its own spec** — the boundary is drawn here on purpose.

## Complexity Tracking

| Trade-off | Why accepted | Alternative rejected because |
|---|---|---|
| **FR-020**: an `OWNER` may be both maker and checker, weakening segregation of duties on purchase approval, payment confirmation and delivery receipt | User decision (2026-07-29) for single-person businesses, where no second employee exists to check. Mitigated by **visibility, not prevention**: `back_office_activity` already stores `actor_employee_id` and `maker_employee_id`, so every single-person approval is listable (SC-010), and the quickstart ships the query | *Never exempt* — leaves a one-person business unable to complete its own purchase flow. *Exempt only when no second employee holds the checker role* — conditional authorization that is materially harder to reason about and to test, for a narrow gain |
| Retiring the `stub` fallback touches **four services** rather than one | FR-018/SC-008: shipping "identity is real" while every service API still grants all roles to an unauthenticated caller would be misleading and insecure | Keeping `stub` in deployed paths — the exact permissive default this feature exists to remove |

**Residual risk (tracked, US4)**: the plain `docker-compose.yml` stack routes through `gateway`, which sets
no `X-Kita-*` headers, so turning `stub` off there refuses everything. US4 must either front that stack
with the edge or label it explicitly as an unauthenticated development stack — and must seed **and
verify** an `OWNER`-linked account *before* the flip (FR-019), or the deployment is unadministerable.
