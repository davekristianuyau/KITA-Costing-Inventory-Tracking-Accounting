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

- [ ] T003 Create `backend/hr-service/src/main/resources/db/migration/V11__account_link_and_roles.sql`: `employee.account_username` (TEXT, **UNIQUE**, nullable), `employee_role` (id, employee_id FK ON DELETE CASCADE, role TEXT, assigned_at, assigned_by, **UNIQUE(employee_id, role)**), `identity_change` (id, employee_id, action `LINKED|UNLINKED|ROLE_GRANTED|ROLE_REVOKED`, account_username nullable, **role nullable**, changed_by, changed_at — append-only). The role actions are what give FR-015/SC-009 somewhere to write; the earlier draft had none.
- [ ] T004 [P] Create `EmployeeRole` entity + top-level `EmployeeRoleRepository` in `backend/hr-service/src/main/java/com/kita/hr/employee/` (`@UuidGenerator`, role stored as an **opaque** String — never an hr enum).
- [ ] T005 [P] Create `IdentityChange` entity + `IdentityChangeRepository` in `backend/hr-service/src/main/java/com/kita/hr/employee/` (append-only audit for link *and* role changes).
- [ ] T006 Add `accountUsername` to `backend/hr-service/src/main/java/com/kita/hr/employee/Employee.java` with its accessor.
- [ ] T007 Add `accountUsername` + `roles` to `backend/hr-service/src/main/java/com/kita/hr/employee/EmployeeResponse.java`. **This deliberately turns `:workflow-service:contractTest` red** (018's drift guard) until T017 — that is the signal working, not a break.
- [ ] T008 [P] **Test first (Constitution II)** — `OWNER` grants everything, and only `OWNER`: `OwnerRoleTest` under `backend/{hr,crm,procurement}-service/src/test/java/.../common/security/` plus `backend/workflow-service/src/test/java/com/kita/workflow/authorization/ActionAuthorizerTest.java`. This is the feature's largest privilege-escalation surface; it must not ship untested.
- [ ] T009 In `CallerContext.roles()` for **hr / crm / procurement** (`backend/{hr,crm,procurement}-service/src/main/java/.../common/security/CallerContext.java`), make **`OWNER` imply every role that service knows**. Leave the `stub` fallback untouched here — it is flipped in US4, so the stack keeps working mid-feature.
- [ ] T010 In `backend/workflow-service/src/main/java/com/kita/workflow/authorization/ActionAuthorizer.java`, short-circuit `permits(...)` to **true when the held roles contain `OWNER`**. ⚠️ **Not** workflow's `CallerContext` — it does not read roles at all (its own javadoc says so); decisions come from `authorization_mapping`, where `OWNER` never appears. Without this an `OWNER` is refused **every** governed action (research Decision 9).

**Checkpoint**: schema + entities + DTO in place; `OWNER` grants everything; contractTest is red by design.

---

## Phase 3: User Story 1 — A signed-in user acts as their own employee (Priority: P1) 🎯 MVP

**Goal**: the acting employee is resolved from the signed-in account, with status and roles read from the
personnel record — no seeded directory, no identity supplied by the browser.

**Independent Test**: link an account to an active employee holding a permitting role → act → succeeds and
is attributed to that employee. An account linked to an employee **without** that role → refused as not
permitted (403), distinctly from every resolution failure.

### Tests first
- [ ] T011 [P] [US1] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/EmployeeByAccountApiTest.java`: by-account lookup returns status **and** roles for a linked employee; **404** when the account is not linked.
- [ ] T012 [P] [US1] Pure unit test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/ActorResolverTest.java`: each `ResolutionOutcome` maps to its own result — `NO_EMPLOYEE_LINKED` / `EMPLOYEE_NOT_ACTIVE` / `EMPLOYEE_MISSING` → 422 with distinct reasons, `UNAVAILABLE` → **503 fail-closed**, and **none** becomes 403 (SC-004).

### Implementation
- [ ] T013 [US1] Add `GET /api/hr/employees/by-account/{username}` to `backend/hr-service/src/main/java/com/kita/hr/api/EmployeeController.java` + the service lookup (one call returns status + roles).
- [ ] T014 [US1] Change `backend/workflow-service/src/main/java/com/kita/workflow/ports/HrPort.java` from `Optional<EmployeeView>` to a `ResolutionOutcome` (5 cases per `data-model.md`).
- [ ] T015 [US1] Rewrite `backend/workflow-service/.../ports/http/HttpHrAdapter.java` to call **by-account** with the username, map 200/404/5xx → outcomes, and **delete `HrPositionRoles.java`** and its `workflow.hr.position-roles` config (018's stand-in, FR-012).
- [ ] T016 [US1] Update `backend/workflow-service/.../ports/fake/InMemoryHrAdapter.java` to the new port, keyed by **account name** (test seam only — no longer a deployed path).
- [ ] T017 [US1] Update `backend/workflow-service/.../actor/ActorResolver.java` to return the distinct outcomes and **fail closed** when HR is unreachable (FR-011); no caching anywhere on this path.
- [ ] T018 [US1] Surface the outcomes in `backend/workflow-service/.../actor/BackOfficePipeline.java` + `api/GlobalExceptionHandler.java`: correct status per outcome, and each attempt recorded in `back_office_activity` (FR-006).
- [ ] T019 [US1] Update `backend/workflow-service/src/contractTest/java/com/kita/workflow/contract/HrEmployeeContractTest.java` to bind hr's **new** `EmployeeResponse` (roles + accountUsername) — turns T007's red green again.
- [ ] T020 [US1] Attribute activity to the **resolved employee id**, not the login name, in `backend/workflow-service/src/main/java/com/kita/workflow/activity/ActivityRecorder.java` + pipeline, with a test asserting two accounts are never conflated (SC-003, US1 scenario 2).

**Checkpoint**: a real login resolves to a real employee and acts with that employee's roles.

---

## Phase 4: User Story 2 — Administer the link, under OWNER (Priority: P2)

**Goal**: an OWNER links accounts to employees, grants roles, sees current links, and unlinks — every change audited.

**Independent Test**: link a new account → that user can act; unlink → refused with a "no employee" reason,
distinct from a permission refusal; a non-OWNER attempting either is refused.

### Tests first
- [ ] T021 [P] [US2] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/AccountLinkApiTest.java`: link/unlink happy paths; the **one-to-one rule refused with 409 in both directions** (account already linked elsewhere; employee already has another account); an `identity_change` row per change (FR-002/FR-009).
- [ ] T022 [P] [US2] hr test in `backend/hr-service/src/test/java/com/kita/hr/api/EmployeeRolesApiTest.java`: role grant/revoke is an **idempotent replace** (a retry cannot double-grant); an **unrecognized token is accepted and grants nothing** downstream (FR-004 scenario 3); every change audited (FR-015).
- [ ] T023 [P] [US2] Pure unit test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/BackOfficePipelineTest.java`: an actor holding `OWNER` **skips** the self-review guard; every non-OWNER maker reviewing their own work is still **422** (FR-020).

### Implementation
- [ ] T024 [US2] Add `PUT` + `DELETE /api/hr/employees/{id}/account` to `EmployeeController` — **`OWNER`-gated** (FR-010/FR-017), one-to-one enforced with a reason naming the conflict, writing an `identity_change` row.
- [ ] T025 [US2] Add `PUT /api/hr/employees/{id}/roles` (**`OWNER`-gated**, idempotent full-set replace, opaque tokens) + audit rows.
- [ ] T026 [P] [US2] Add `GET /api/hr/account-links` in a new `backend/hr-service/src/main/java/com/kita/hr/api/AccountLinkController.java` (privileged read: who can act as whom).
- [ ] T027 [US2] In `backend/workflow-service/.../actor/BackOfficePipeline.java`, skip the self-review guard when the acting employee holds `OWNER` (FR-020); leave it enforced for everyone else.
- [ ] T028 [US2] Make account names permanent in `backend/identity-service/src/main/java/com/kita/identity/domain/AppUser.java` + `auth/AuthService.java`: no rename path, and a deactivated account's username is never reissued (FR-016) — with a test, since this is what stops an identity transferring to a new hire.
- [ ] T029 [P] [US2] Add the single-person-approval query to `backend/workflow-service/README.md` + a test asserting an OWNER self-approval is listable via `maker_employee_id = actor_employee_id` (SC-010) — **no schema change**.

**Checkpoint**: joiners/leavers are administered without touching data by hand; privilege grants are audited.

---

## Phase 5: User Story 3 — Leavers lose access immediately (Priority: P2)

**Goal**: separation or inactivity in the personnel record removes back-office access on the next action.

**Independent Test**: act successfully → mark the employee `SEPARATED` → retry the same action → refused,
citing the status, and recorded.

- [ ] T030 [P] [US3] Test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/ActorResolverTest.java`: a non-`ACTIVE` employee is refused **422 naming the status** (not 403); a separation that happens **mid-session** bites on the very next action, with no re-login (SC-002).
- [ ] T031 [US3] Ensure `EMPLOYEE_NOT_ACTIVE` carries the actual status into the reason in `ActorResolver`/`HttpHrAdapter`, distinct from `NO_EMPLOYEE_LINKED` and from a permission refusal.
- [ ] T032 [US3] **FR-013 guard**: regression test in `backend/workflow-service/src/test/java/com/kita/workflow/authorization/ActionAuthorizerTest.java` asserting the seeded `authorization_mapping` grants and the outcome taxonomy are unchanged — the **only** permitted deviation is the FR-020 `OWNER` exemption.
- [ ] T033 [US3] Add a guard test in `backend/workflow-service/src/test/java/com/kita/workflow/actor/ActorResolverTest.java` proving the resolution path is **not cached** anywhere (a role/status change must apply on the next request) — the property SC-002/SC-006 depend on.

**Checkpoint**: the personnel record genuinely governs access, moment to moment.

---

## Phase 6: User Story 4 — The personnel record is the only source of roles (Priority: P3)

**Goal**: no deployed path resolves an employee or a role from a seeded directory or a permissive default.

⚠️ **Widest slice, and the split point** — if 017 must ship sooner, this becomes its own spec.

- [ ] T034 [P] [US4] Rewrite `backend/identity-service/src/main/java/com/kita/identity/config/DemoSeeder.java`: create real employees with **real account links** and at least one **OWNER**, dropping the `emp-*` magic-name trick (FR-012).
- [ ] T035 [US4] In `backend/edge-gateway/src/main/java/com/kita/edge/SessionAuthFilter.java`, resolve account → employee → roles from hr **per request** and set the trusted `X-Kita-Roles`; **never** read roles from the token (research Decision 7); hr unreachable ⇒ refuse as temporarily unavailable (FR-011). **Keep `EdgeRoutingIT` green** — it already asserts inbound `X-Kita-*` spoofing is stripped, which is what guarantees FR-003, and this task edits that very filter.
- [ ] T036 [P] [US4] Edge test in `backend/edge-gateway/src/test/java/com/kita/edge/SessionAuthFilterTest.java`: roles reflect a change on the **next request** without re-login; hr down ⇒ request **refused, never granted**.
- [ ] T037 [P] [US4] **Test first**: for hr/crm/procurement, an absent `X-Kita-Roles` header grants **nothing** (previously: all roles) — one test per service under `.../common/security/` (FR-018, SC-008).
- [ ] T038 [US4] Flip `stub` to `false` in `backend/{hr,crm,procurement}-service/src/main/resources/application.yml` — the *role* fallback ("no role header ⇒ all roles").
- [ ] T039 [US4] Separately retire workflow's **identity** fallback (`workflow.security.stub` in `backend/workflow-service/src/main/resources/application.yml`): a *different* mechanism — "no `X-Kita-User` ⇒ fabricate a stub employee holding all roles". Conflating the two would leave the more dangerous one in place. Keep both only as isolated `:service:test` seams.
- [ ] T040 [US4] Add the FR-019 preflight in `backend/edge-gateway/src/main/java/com/kita/edge/` (startup check) plus a documented step in `specs/017-account-employee-identity/quickstart.md`: verify at least one account resolves to an employee holding `OWNER` **before** `stub` is disabled — otherwise the deployment is unadministerable.
- [ ] T041 [US4] Update `docker-compose.yml`, `docker-compose.mtls.yml` and `sim/`: `HR_ADAPTER=http`, `stub` off, and **resolve the base-stack risk as decided**: the plain `gateway` sets no `X-Kita-*` headers, so `docker-compose.yml` is labelled an **unauthenticated development stack** and its permissive mode becomes an explicit, loud opt-in (`KITA_DEV_NO_AUTH=true`) — never a silent fallback (SC-008).
- [ ] T042 [P] [US4] Guard test + `grep` check: no `HrPositionRoles`/`position-roles` remains, and no deployed path resolves employees from seeded login names (SC-005), **and** roles are read from no source outside the personnel record (FR-014).

**Checkpoint**: identity is real everywhere it is claimed to be real.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T043 [P] Document the new hr endpoints in `specs/004-hr-payroll/contracts/hr-openapi.yaml` (the source of truth `OpenApiContractTest` reads) — this should **fix `OpenApiContractTest`**, the long-standing red on `main` (see [[kita-ci-known-red-jobs]]).
- [ ] T044 [P] READMEs: `backend/hr-service/README.md` (link, roles, OWNER, audit), `backend/workflow-service/README.md` (resolution outcomes; delete the now-obsolete `position-roles` section), `backend/edge-gateway/README.md` (per-request role resolution).
- [ ] T045 Run `./gradlew spotlessApply build` from `backend/` — green against the documented baseline, with T038 removing the hr red.
- [ ] T046 Run `specs/017-account-employee-identity/quickstart.md` end to end on the composed stack (SC-001…SC-010), including the single-person-approval query and the hr-down fail-closed check.
- [ ] T047 [P] Capture context to the project memory directory via the `kita-context-capture` skill (the false-assumption lesson, the "roles never in the token" rationale, the OWNER trade-off, the base-stack risk).

---

## Dependencies & Execution Order

- **Setup (P1)** → no deps.
- **Foundational (P2)** → after Setup; **blocks every story** (T003 schema → T004–T007; T007 reddens contractTest until T043).
- **US1** → after Foundational. T043 (hr endpoint) before T043 (caller). T046 before T043–T046.
- **US2** → after US1 (administration presumes resolution works). T046/T043 independent of T043/T046.
- **US3** → after US1; small, mostly assertions on the outcome taxonomy.
- **US4** → after US1–US3 (needs real links to exist before stand-ins are removed, and an OWNER before `stub` is flipped — T043 and T045 gate T043/T046).
- **Polish (P7)** → after the desired stories.

### Within a story
Tests first (they encode the refusal semantics) → schema/entities → hr endpoint → caller → wiring.

### Parallel opportunities
- Setup T001/T002 together.
- Foundational T004/T005 together (different files) — both after T003.
- US1 tests T043/T046 together; US2 tests T043/T046/T043 together.
- US4 T043 and T046/T047 are independent of the edge change itself.

---

## Parallel Example: US2 tests (write first)
```bash
Task: "hr link/unlink + one-to-one 409 + audit"      # T043
Task: "hr role grant/revoke idempotent + opaque"     # T046
Task: "workflow OWNER skips self-review, others 422" # T043
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
- **T007 will redden `:workflow-service:contractTest` on purpose** — 018's guard catching a real DTO change. Fix it in T043, not by weakening the test.
- Roles must **never** be written into the session token (research Decision 7) — if a task seems to need that, it is the wrong task.
- FR-020 needs **no** schema change: `back_office_activity` already stores `actor_employee_id` + `maker_employee_id`.
- Commit per task or logical group; simple messages, no AI attribution (PR body may include it).
