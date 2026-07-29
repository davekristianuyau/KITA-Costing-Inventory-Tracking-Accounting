# Implementation Plan: Account-to-Employee Identity

**Branch**: `017-account-employee-identity` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-account-employee-identity/spec.md`

## Summary

Nothing connects a login account to an employee record: `edge-gateway` sets `X-Kita-User` to the
**username**, while `workflow-service` looks employees up by **HR UUID**. The demo only works because
`DemoSeeder` names logins after the in-memory fake's employee ids (`emp-sales`…). This feature makes the
personnel record the real source of identity: **hr-service** gains the account link *and* the back-office
roles it never had, workflow resolves the acting employee by account username, and both stand-ins are
retired. `identity-service`, `TokenService` and `edge-gateway` are **unchanged** — the edge already strips
inbound `X-Kita-*` and asserts the identity, so FR-003 holds as-is. See [research.md](./research.md).

> **Spec correction (Phase 0)**: the spec assumes *"status and roles are already maintained in the
> personnel system."* Status yes; **roles no** — hr-service stores none, and its own `Role` enum governs
> HR's own API. FR-004 is unachievable without adding role storage, so that is in scope here. Settled
> with the user on 2026-07-29 (research Decision 2).

## Technical Context

**Language/Version**: Java 17; Spring Boot 3.5.0 (Gradle wrapper 8.10.2)
**Primary Dependencies**: Spring Web / Data JPA / Validation, Flyway, Jakarta Bean Validation; the existing
`RemoteCall` + `contractTest` harness (018) on the caller side
**Storage**: PostgreSQL, schema-per-service. **hr-service only**: 1 new column (`employee.account_username`)
+ 2 new tables (`employee_role`, `account_link_change`) in a single Flyway `V11`. No other service gains schema.
**Testing**: JUnit 5 + AssertJ pure unit tests (local); Testcontainers ITs + `*ApiContractTest` (Docker);
workflow's `contractTest` source set binds to hr's **real** DTOs, so this change is drift-checked by construction
**Target Platform**: Linux containers — composed stack + Floci; one isolated deployment per client
**Project Type**: Backend microservices (monorepo `backend/`); no frontend change
**Performance Goals**: one HR round trip per governed action (already the case for status); **deliberately
uncached** — caching would reintroduce the revocation delay this design exists to remove
**Constraints**: FR-003 already satisfied (edge strips + asserts); FR-011 **fail closed** when HR is
unreachable; FR-013 authorization rules unchanged; the four resolution failures must stay distinguishable (SC-004)
**Scale/Scope**: hr-service (link + roles + admin + audit), workflow-service (resolve by username, outcome
taxonomy, delete `HrPositionRoles`), identity `DemoSeeder` (real links), compose (`HR_ADAPTER=http`)

## Constitution Check

*GATE: must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| I. Specification-Driven | ✅ Spec → plan → artifacts, prioritized US1–US4. Phase 0 corrected a false spec assumption rather than building on it. |
| II. TDD (NON-NEGOTIABLE) | ✅ Each slice is test-first: the resolution-outcome taxonomy and the one-to-one rule are pure-unit testable; `contractTest` goes red the moment hr's DTO changes, driving the caller fix. |
| III. Security & Data Integrity (NON-NEGOTIABLE) | ✅ **This is a security feature.** Roles are server-resolved per action (never caller-asserted), revocation is immediate, unreachable HR fails **closed**, link/role changes are audited, administration is `HR_ADMIN`-gated. |
| IV. Environment Isolation | ✅ One deployment per client; hr has no tenant concept, so a link cannot span clients. No prod data in dev. |
| V. Observability | ✅ Four distinguishable outcomes, each recorded in `back_office_activity`; every link/role change records who + when. |
| VI. Simplicity & YAGNI | ✅ Chose the option leaving identity/token/edge **untouched**; one migration, no new module, no new header, no cache. |
| VII. Automated Quality Gates | ✅ `:hr-service:build` + `:workflow-service:build` (incl. `contractTest`) gate merges; 017 should also **fix** hr's long-standing `OpenApiContractTest` red by documenting the new endpoints. |

**Result: PASS** — no violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)
```text
specs/017-account-employee-identity/
├── plan.md              # this file
├── research.md          # Phase 0 — 5 decisions + what the code actually does
├── data-model.md        # Phase 1 — Employee(+link), EmployeeRole, AccountLinkChange, Resolution Outcome
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
│   ├── AccountLinkChange.java + repository            #   NEW — link/role audit (who, when)
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

backend/identity-service/.../config/DemoSeeder.java    # real employees + links, not emp-* magic names
docker-compose.yml, sim/                               # HR_ADAPTER: fake → http (016's note names 017)
```

**Structure Decision**: hr-service takes the new state and endpoints; workflow-service changes only how it
resolves the actor. **identity-service, `TokenService` and `edge-gateway` are untouched** — the single
biggest scope saving, and the reason FR-003 needs no work.

## Phasing (maps to user stories, MVP-first)

- **US1 (P1, MVP)** — link + roles in hr, resolution by username in workflow, activity attributed to the
  real employee. The smallest slice that removes the test double from the authorization path.
- **US2 (P2)** — link administration: link / list / unlink, one-to-one enforcement (409), audit.
- **US3 (P2)** — leavers: non-`ACTIVE` refused with a status-naming reason; a live session doesn't extend access.
- **US4 (P3)** — retire both stand-ins (`HrPositionRoles`, `emp-*` seeded logins), flip `HR_ADAPTER=http`,
  prove no deployed path resolves from a seeded directory.

## Complexity Tracking

No Constitution violations — section intentionally empty.
