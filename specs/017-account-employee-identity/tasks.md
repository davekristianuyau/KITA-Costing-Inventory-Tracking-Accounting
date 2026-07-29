---
description: "Task list for 017 — Account-to-Employee Identity"
---

# Tasks: Account-to-Employee Identity

**Input**: Design documents from `/specs/017-account-employee-identity/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/hr-identity-api.md

**Tests**: Included — Constitution II (TDD) is non-negotiable and this is a security feature: every
refusal path is a test before it is code.

**Organization**: By user story. US1 (P1, resolve) → US2 (P2, admin + OWNER) → US3 (P2, leavers) →
US4 (P3, retire stand-ins + `stub`). **US4 is the deliberate split point** — US1–US3 stand alone if 017
must ship sooner.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no incomplete-task dependency)
- Backend monorepo root: `backend/`. Owner of new state: `hr-service`.

---

## Phase 1: Setup (Shared vocabulary)

- [ ] T001 [P] Add the `OWNER` token to every role vocabulary that authorizes: `backend/hr-service/src/main/java/com/kita/hr/common/security/Role.java`, `backend/crm-service/.../crm/common/security/Role.java`, `backend/procurement-service/.../procurement/common/security/Role.java`, `backend/workflow-service/.../workflow/common/security/Role.java`.
- [ ] T002 [P] Add a canonical role-token fixture (the one flat vocabulary from `contracts/hr-identity-api.md`, incl. `OWNER`) shared by hr tests and `backend/workflow-service/src/contractTest/java/com/kita/workflow/contract/`, so the four services cannot drift on spelling.

**Checkpoint**: every service agrees on the token set; nothing behavioural has changed yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: the storage and the DTO change every story depends on. No user-story work starts first.

- [ ] T003 Create `backend/hr-service/src/main/resources/db/migration/V11__account_link_and_roles.sql`: `employee.account_username` (TEXT, **UNIQUE**, nullable), `employee_role` (id, employee_id FK ON DELETE CASCADE, role TEXT, assigned_at, assigned_by, **UNIQUE(employee_id, role)**), `account_link_change` (id, employee_id, account_username, action, changed_by, changed_at — append-only).
- [ ] T004 [P] Create `EmployeeRole` entity + top-level `EmployeeRoleRepository` in `backend/hr-service/src/main/java/com/kita/hr/employee/` (`@UuidGenerator`, role stored as an **opaque** String — never an hr enum).
- [ ] T005 [P] Create `AccountLinkChange` entity + `AccountLinkChangeRepository` in `backend/hr-service/src/main/java/com/kita/hr/employee/` (append-only audit for link *and* role changes).
- [ ] T006 Add `accountUsername` to `backend/hr-service/src/main/java/com/kita/hr/employee/Employee.java` with its accessor.
- [ ] T007 Add `accountUsername` + `roles` to `backend/hr-service/src/main/java/com/kita/hr/employee/EmployeeResponse.java`. **This deliberately turns `:workflow-service:contractTest` red** (018's drift guard) until T017 — that is the signal working, not a break.
- [ ] T008 In all four `CallerContext` classes (hr/crm/procurement/workflow `common/security/`), make **`OWNER` imply every role that service knows**. Leave the `stub` fallback untouched here — it is flipped in US4 (T034), so the stack keeps working mid-feature.

**Checkpoint**: schema + entities + DTO in place; `OWNER` grants everything; contractTest is red by design.

---

## Phase 3: User Story 1 — A signed-in user acts as their own employee (Priority: P1) 🎯 MVP

**Goal**: the acting employee is resolved from the signed-in account, with status and roles read from the
personnel record — no seeded directory, no identity supplied by the browser.

**Independent Test**: link an account to an active employee holding a permitting role → act → succeeds and
is attributed to that employee. An account linked to an employee **without** that role → refused as not
permitted (403), distinctly from every resolution failure.

### Tests first
- [ ] T009 [P] [US1] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/`: by-account lookup returns status **and** roles for a linked employee; **404** when the account is not linked.
- [ ] T010 [P] [US1] Pure unit test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/`: each `ResolutionOutcome` maps to its own result — `NO_EMPLOYEE_LINKED` / `EMPLOYEE_NOT_ACTIVE` / `EMPLOYEE_MISSING` → 422 with distinct reasons, `UNAVAILABLE` → **503 fail-closed**, and **none** becomes 403 (SC-004).

### Implementation
- [ ] T011 [US1] Add `GET /api/hr/employees/by-account/{username}` to `backend/hr-service/src/main/java/com/kita/hr/api/EmployeeController.java` + the service lookup (one call returns status + roles).
- [ ] T012 [US1] Change `backend/workflow-service/src/main/java/com/kita/workflow/ports/HrPort.java` from `Optional<EmployeeView>` to a `ResolutionOutcome` (5 cases per `data-model.md`).
- [ ] T013 [US1] Rewrite `backend/workflow-service/.../ports/http/HttpHrAdapter.java` to call **by-account** with the username, map 200/404/5xx → outcomes, and **delete `HrPositionRoles.java`** and its `workflow.hr.position-roles` config (018's stand-in, FR-012).
- [ ] T014 [US1] Update `backend/workflow-service/.../ports/fake/InMemoryHrAdapter.java` to the new port, keyed by **account name** (test seam only — no longer a deployed path).
- [ ] T015 [US1] Update `backend/workflow-service/.../actor/ActorResolver.java` to return the distinct outcomes and **fail closed** when HR is unreachable (FR-011); no caching anywhere on this path.
- [ ] T016 [US1] Surface the outcomes in `backend/workflow-service/.../actor/BackOfficePipeline.java` + `api/GlobalExceptionHandler.java`: correct status per outcome, and each attempt recorded in `back_office_activity` (FR-006).
- [ ] T017 [US1] Update `backend/workflow-service/src/contractTest/java/com/kita/workflow/contract/HrEmployeeContractTest.java` to bind hr's **new** `EmployeeResponse` (roles + accountUsername) — turns T007's red green again.
- [ ] T018 [US1] Attribute activity to the **resolved employee id**, not the login name, in `backend/workflow-service/src/main/java/com/kita/workflow/activity/ActivityRecorder.java` + pipeline, with a test asserting two accounts are never conflated (SC-003, US1 scenario 2).

**Checkpoint**: a real login resolves to a real employee and acts with that employee's roles.

---

## Phase 4: User Story 2 — Administer the link, under OWNER (Priority: P2)

**Goal**: an OWNER links accounts to employees, grants roles, sees current links, and unlinks — every change audited.

**Independent Test**: link a new account → that user can act; unlink → refused with a "no employee" reason,
distinct from a permission refusal; a non-OWNER attempting either is refused.

### Tests first
- [ ] T019 [P] [US2] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/AccountLinkApiTest.java`: link/unlink happy paths; the **one-to-one rule refused with 409 in both directions** (account already linked elsewhere; employee already has another account); an `account_link_change` row per change (FR-002/FR-009).
- [ ] T020 [P] [US2] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/EmployeeRolesApiTest.java`: role grant/revoke is an **idempotent replace** (a retry cannot double-grant); an **unrecognized token is accepted and grants nothing** downstream (FR-004 scenario 3); every change audited (FR-015).
- [ ] T021 [P] [US2] Pure unit test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/BackOfficePipelineTest.java`: an actor holding `OWNER` **skips** the self-review guard; every non-OWNER maker reviewing their own work is still **422** (FR-020).

### Implementation
- [ ] T022 [US2] Add `PUT` + `DELETE /api/hr/employees/{id}/account` to `EmployeeController` — **`OWNER`-gated** (FR-010/FR-017), one-to-one enforced with a reason naming the conflict, writing `AccountLinkChange`.
- [ ] T023 [US2] Add `PUT /api/hr/employees/{id}/roles` (**`OWNER`-gated**, idempotent full-set replace, opaque tokens) + audit rows.
- [ ] T024 [P] [US2] Add `GET /api/hr/account-links` in a new `backend/hr-service/src/main/java/com/kita/hr/api/AccountLinkController.java` (privileged read: who can act as whom).
- [ ] T025 [US2] In `backend/workflow-service/.../actor/BackOfficePipeline.java`, skip the self-review guard when the acting employee holds `OWNER` (FR-020); leave it enforced for everyone else.
- [ ] T026 [US2] Make account names permanent in `backend/identity-service/src/main/java/com/kita/identity/domain/AppUser.java` + `auth/AuthService.java`: no rename path, and a deactivated account's username is never reissued (FR-016) — with a test, since this is what stops an identity transferring to a new hire.
- [ ] T027 [P] [US2] Add the single-person-approval query to `backend/workflow-service/README.md` + a test asserting an OWNER self-approval is listable via `maker_employee_id = actor_employee_id` (SC-010) — **no schema change**.

**Checkpoint**: joiners/leavers are administered without touching data by hand; privilege grants are audited.

---

## Phase 5: User Story 3 — Leavers lose access immediately (Priority: P2)

**Goal**: separation or inactivity in the personnel record removes back-office access on the next action.

**Independent Test**: act successfully → mark the employee `SEPARATED` → retry the same action → refused,
citing the status, and recorded.

- [ ] T028 [P] [US3] Test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/ActorResolverTest.java`: a non-`ACTIVE` employee is refused **422 naming the status** (not 403); a separation that happens **mid-session** bites on the very next action, with no re-login (SC-002).
- [ ] T029 [US3] Ensure `EMPLOYEE_NOT_ACTIVE` carries the actual status into the reason in `ActorResolver`/`HttpHrAdapter`, distinct from `NO_EMPLOYEE_LINKED` and from a permission refusal.
- [ ] T030 [US3] Add a guard test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/ActorResolverTest.java` proving the resolution path is **not cached** anywhere (a role/status change must apply on the next request) — the property SC-002/SC-006 depend on.

**Checkpoint**: the personnel record genuinely governs access, moment to moment.

---

## Phase 6: User Story 4 — The personnel record is the only source of roles (Priority: P3)

**Goal**: no deployed path resolves an employee or a role from a seeded directory or a permissive default.

⚠️ **Widest slice, and the split point** — if 017 must ship sooner, this becomes its own spec.

- [ ] T031 [P] [US4] Rewrite `backend/identity-service/src/main/java/com/kita/identity/config/DemoSeeder.java`: create real employees with **real account links** and at least one **OWNER**, dropping the `emp-*` magic-name trick (FR-012).
- [ ] T032 [US4] In `backend/edge-gateway/src/main/java/com/kita/edge/SessionAuthFilter.java`, resolve account → employee → roles from hr **per request** and set the trusted `X-Kita-Roles`; **never** read roles from the token (research Decision 7); hr unreachable ⇒ refuse as temporarily unavailable (FR-011).
- [ ] T033 [P] [US4] Edge test in `backend/edge-gateway/src/test/java/com/kita/edge/SessionAuthFilterTest.java`: roles reflect a change on the **next request** without re-login; hr down ⇒ request **refused, never granted**.
- [ ] T034 [US4] Flip the `stub` default to `false` in `backend/{hr,crm,procurement,workflow}-service/src/main/resources/application.yml` so an absent role header grants **nothing**; keep `stub` only as an isolated `:service:test` seam (FR-018, SC-008).
- [ ] T035 [US4] Add the FR-019 preflight in `backend/edge-gateway/src/main/java/com/kita/edge/` (startup check) plus a documented step in `specs/017-account-employee-identity/quickstart.md`: verify at least one account resolves to an employee holding `OWNER` **before** `stub` is disabled — otherwise the deployment is unadministerable.
- [ ] T036 [US4] Update `docker-compose.yml`, `docker-compose.mtls.yml` and `sim/`: `HR_ADAPTER=http`, `stub` off, and **resolve the base-stack risk** — the plain `gateway` sets no `X-Kita-*` headers, so either front that stack with the edge or label it explicitly an unauthenticated development stack (plan Complexity Tracking).
- [ ] T037 [P] [US4] Guard test + `grep` check: no `HrPositionRoles`/`position-roles` remains, and no deployed path resolves employees from seeded login names (SC-005).

**Checkpoint**: identity is real everywhere it is claimed to be real.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T038 [P] Document the new hr endpoints in `specs/004-hr-payroll/contracts/hr-openapi.yaml` (the source of truth `OpenApiContractTest` reads) — this should **fix `OpenApiContractTest`**, the long-standing red on `main` (see [[kita-ci-known-red-jobs]]).
- [ ] T039 [P] READMEs: `backend/hr-service/README.md` (link, roles, OWNER, audit), `backend/workflow-service/README.md` (resolution outcomes; delete the now-obsolete `position-roles` section), `backend/edge-gateway/README.md` (per-request role resolution).
- [ ] T040 Run `./gradlew spotlessApply build` from `backend/` — green against the documented baseline, with T038 removing the hr red.
- [ ] T041 Run `specs/017-account-employee-identity/quickstart.md` end to end on the composed stack (SC-001…SC-010), including the single-person-approval query and the hr-down fail-closed check.
- [ ] T042 [P] Capture context to the project memory directory via the `kita-context-capture` skill (the false-assumption lesson, the "roles never in the token" rationale, the OWNER trade-off, the base-stack risk).

---

## Dependencies & Execution Order

- **Setup (P1)** → no deps.
- **Foundational (P2)** → after Setup; **blocks every story** (T003 schema → T004–T007; T007 reddens contractTest until T017).
- **US1** → after Foundational. T011 (hr endpoint) before T013 (caller). T012 before T013–T016.
- **US2** → after US1 (administration presumes resolution works). T022/T023 independent of T025/T026.
- **US3** → after US1; small, mostly assertions on the outcome taxonomy.
- **US4** → after US1–US3 (needs real links to exist before stand-ins are removed, and an OWNER before `stub` is flipped — T031 and T035 gate T034/T036).
- **Polish (P7)** → after the desired stories.

### Within a story
Tests first (they encode the refusal semantics) → schema/entities → hr endpoint → caller → wiring.

### Parallel opportunities
- Setup T001/T002 together.
- Foundational T004/T005 together (different files) — both after T003.
- US1 tests T009/T010 together; US2 tests T019/T020/T021 together.
- US4 T031 and T033/T037 are independent of the edge change itself.

---

## Parallel Example: US2 tests (write first)
```bash
Task: "hr link/unlink + one-to-one 409 + audit"      # T019
Task: "hr role grant/revoke idempotent + opaque"     # T020
Task: "workflow OWNER skips self-review, others 422" # T021
```

## Implementation Strategy

### MVP (US1)
Setup → Foundational → US1. **Stop and validate**: a real login resolves to a real employee, acts with
that employee's roles, and every resolution failure is distinguishable. That alone removes the test double
from the authorization path.

### Incremental delivery
1. **US1** → identity is real for the back office (MVP).
2. **US2** → joiners/leavers administered under OWNER, every privilege change audited.
3. **US3** → separation bites immediately.
4. **US4** → the stand-ins and the permissive default are gone system-wide *(splittable)*.
5. **Polish** → docs, the OpenAPI red fixed, quickstart, memory.

## Notes
- [P] = different files/modules, no incomplete-task dependency.
- **T007 will redden `:workflow-service:contractTest` on purpose** — 018's guard catching a real DTO change. Fix it in T017, not by weakening the test.
- Roles must **never** be written into the session token (research Decision 7) — if a task seems to need that, it is the wrong task.
- FR-020 needs **no** schema change: `back_office_activity` already stores `actor_employee_id` + `maker_employee_id`.
- Commit per task or logical group; simple messages, no AI attribution (PR body may include it).
