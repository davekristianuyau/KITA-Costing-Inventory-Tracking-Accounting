# Research: Account-to-Employee Identity (017)

Phase 0 output. Grounded in the code, not the spec's assumptions — one of which turned out to be false.

## What the code actually does today

| Piece | Reality |
|---|---|
| `identity-service` `AppUser` | `id, client, username, passwordHash, active, failedAttempts, lockedUntil`. **No employee link.** |
| `AuthService` | issues the token with `tokenService.issue(username, companyId, List.of())` — **roles are always empty** |
| `TokenService` | claims: `subject` (= username), `client`, `roles` |
| `edge-gateway` `SessionAuthFilter` | **strips all inbound `X-Kita-*`** (anti-spoofing) and sets trusted `X-Kita-User` (= subject/username), `X-Kita-Client`, `X-Kita-Roles` |
| `hr-service` `Employee` | no roles, no account link. Its own `Role` enum (`HR_ADMIN`/`PAYROLL_OFFICER`/`MANAGER`/`EMPLOYEE_SELF`) governs **HR's own API**, not back-office actions |
| `hr-service` `CallerContext` | already reads **`X-Kita-Employee-Id`** — a header **nothing currently sets**. A hook pre-wired for this feature. |
| `workflow-service` `ActorResolver` | `X-Kita-User` → `HrPort.getEmployee(id)` → active check + role set |
| `identity-service` `DemoSeeder` | creates logins named **exactly** after the workflow fake's employee ids (`emp-sales`, `emp-cashier`, …) |

**The gap in one sentence**: identity has no employee link and no roles; HR has employees but no roles;
the demo works *only* because a login name happens to equal an `InMemoryHrAdapter` employee id.

**Spec assumption that is false**: *"Employee status and roles are already maintained in the personnel
system."* Status yes; **roles no**. FR-004 cannot be met without adding them.

## Decision 1 — the account↔employee link lives in **hr-service**

**Decision**: `Employee` gains an account reference (the login username), unique within the service.
Resolution is `GET /api/hr/employees/by-account/{username}`.

**Rationale**: it makes **identity-service, `TokenService` and `edge-gateway` completely unchanged**.
The username already travels as `X-Kita-User`, and the edge already **strips inbound `X-Kita-*`** before
setting it — so FR-003 ("travels with the session, the client cannot supply or alter it") is *already
satisfied by construction*; no new claim, no new header, no change to what every other service treats as
the actor. HR then owns employee + status + roles + link, which is exactly the single source of truth
FR-004 demands. Client scoping (spec Assumption) is structural: each client gets an isolated deployment
and hr-service has no tenant concept, so a link cannot span clients.

**Alternatives rejected**:
- *Link in identity-service (`AppUser.employee_id`)* — FR-001's literal wording, but it changes identity,
  the token, the edge, and every service's meaning of `X-Kita-User`; and identity would store an HR UUID
  it cannot validate without a new identity→HR call. Much larger blast radius for the same outcome.
- *A separate link table/service* — a third participant in every resolution, for a one-to-one join.

**Note on FR-001's wording**: the requirement is that an account *has* an employee identity, not that the
account row physically stores it. The link is one-to-one either way; only its custodian differs.

## Decision 2 — back-office roles are **stored in hr-service**

**Decision**: add assigned back-office roles to the employee record (`employee_role`), administered
through hr-service, and return them on the employee read.

**Rationale (security, not bookkeeping)**:
1. **Roles must never be caller-asserted.** Roles in the session token mean the *token* carries authority
   — a minting bug, key compromise or replay grants them directly. Resolved server-side per action, a
   stolen or stale token still cannot grant what HR does not say. This is what FR-004 is protecting.
2. **Revocation must be immediate.** HR-resolved roles apply on the **next action** (SC-002/SC-006).
   Token-embedded roles survive until expiry — a separated employee keeps access for the token's life.
3. **No split-brain.** One record answers both "still employed?" and "may do what?", so the two cannot
   disagree; two stores can (separated in HR, still privileged elsewhere).
4. **Least privilege is administrable.** Per-employee roles grant exactly what a person needs; a
   position→roles map grants whatever the position blanket-grants (018's dev mapping gave one position
   all nine roles — fine for a demo, wrong for production).

**Alternatives rejected**:
- *Keep 018's `workflow.hr.position-roles` config* — **fails the spec's own success criteria**: SC-001
  (new hire, zero redeploys) and SC-006 (role change applies next action) are impossible when roles live
  in deployment config. Also contradicts FR-012, which requires removing the stand-ins.
- *Roles in identity / the token's empty `roles` claim* — directly contradicts FR-004 and leaves two
  competing role sources.

**Accepted cost**: real work inside spec 004's service (table, migration, admin endpoints, change audit),
plus an HR lookup per governed action. The lookup already happens today for status, so it is not a new
hop; it is deliberately **not cached** — caching would reintroduce exactly the revocation delay
Decision 2 exists to remove.

## Decision 3 — four distinguishable resolution outcomes (FR-005/006/011, SC-004)

**Decision**: `HrPort` returns a resolution *outcome*, not an `Optional`:

| Outcome | Cause | Surfaced as |
|---|---|---|
| `RESOLVED` | active employee, roles read | proceed |
| `NO_EMPLOYEE_LINKED` | account has no employee | 422, reason "account has no employee" |
| `EMPLOYEE_NOT_ACTIVE` | status ≠ ACTIVE (inactive/separated) | 422, reason naming the status |
| `EMPLOYEE_MISSING` | link points at a record that is gone | 422, distinct reason |
| `UNAVAILABLE` | HR unreachable/5xx after retries | **503, fail closed** (FR-011) |

None of these may be conflated with `REJECTED_NOT_PERMITTED` (403), which remains "resolved, but the
roles don't grant it". Each is recorded in `back_office_activity` like any other attempt (FR-006).

**Rationale**: today `getEmployee` returns `Optional`, which collapses "no link", "missing" and "inactive"
into one indistinguishable empty — SC-004 requires them apart. **Alternative rejected**: exceptions per
case (loses the single audit path the pipeline already has).

## Decision 3b — account names are permanent (FR-016)

**Decision**: `identity-service` treats a username as permanent — no rename, and a deactivated account's
username is never reissued. Enforced where accounts are administered/seeded, and stated in the account
contract.

**Rationale**: the link is keyed on the username (Decision 1). Without this rule, renaming an account —
or reissuing a departed person's name to a new hire — silently transfers the linked employee's identity
**and roles** to someone else. That is a privilege-escalation path, not a data-tidiness concern.
**Alternative rejected**: keying on identity's account UUID — stable by construction, but the request only
carries the username, so hr would need a username→id translation from identity on every governed action,
reintroducing the cross-service dependency the hr-side link was chosen to avoid.

## Decision 4 — retire both stand-ins (FR-012)

**Decision**: remove (a) 018's `workflow.hr.position-roles` map + `HrPositionRoles`, and (b) the
`emp-*`-named seeded-login trick. `InMemoryHrAdapter` **stays** as a build/test seam for
`:workflow-service:test` (isolated builds), but is no longer a *deployed* path: the composed and Floci
stacks set `HR_ADAPTER=http`, and `DemoSeeder` creates real employees + links rather than magic names.

**Rationale**: FR-012 says the stand-in must be gone from every *deployed* path and the sim's logins
migrated to real links; it does not require deleting the unit-test double (the 018 lesson was that fakes
must be *held to the real contract*, not removed). 016's `HR_ADAPTER=fake` default flips to `http` once
this lands — that comment in `docker-compose.yml` names 017 as the trigger.

## Decision 5 — hr-service's pre-wired `X-Kita-Employee-Id` becomes real

**Decision**: leave the edge unchanged; hr resolves the caller's own employee from `X-Kita-User` via the
new link, so `CallerContext.employeeId()` (already written, currently always empty) starts returning a
value and `EMPLOYEE_SELF` self-service authorization works for the first time.

**Rationale**: a free correctness win from Decision 1, with no new header. **Alternative rejected**:
having the edge set `X-Kita-Employee-Id` — it would have to call HR during request routing.

## Decision 6 — one flat role vocabulary; `OWNER` universally means "everything" (FR-017)

**Decision**: an employee holds a flat set of **opaque role tokens** in hr-service. Each service
recognizes the subset it cares about — workflow's (`SALES`, `PROCUREMENT_APPROVER`, …), hr's own
(`HR_ADMIN`, `PAYROLL_OFFICER`, …), crm's, procurement's — and ignores the rest (FR-004 scenario 3).
**`OWNER` is recognized by every service as implying all of its roles**, implemented in each
`CallerContext` as a single "if OWNER, grant" branch.

**Rationale**: one store, one administration screen, one audit trail. A second "service role" vocabulary
would double the storage and the admin surface for no user-visible gain, and would make "who can do what"
answerable only by joining two lists. Opaque tokens mean a service can add a role without an hr redeploy.

**Alternatives rejected**: separate back-office vs service-API role tables (two places to grant, easy to
leave inconsistent); `OWNER` as a boolean flag on the employee rather than a token (a second mechanism to
audit and reason about, when it is conceptually just the most powerful role).

## Decision 7 — roles are resolved **per request at the edge**, never carried in the token (FR-018)

**Decision**: `edge-gateway` — which already validates the session on every request and already sets
`X-Kita-Roles` — resolves account → employee → roles from hr-service **per request** and sets the trusted
header. Every service keeps its existing `CallerContext` and simply stops defaulting to all-roles when the
header is absent. Unreachable hr ⇒ **fail closed** (FR-011).

**Rationale**: the obvious-looking alternative is wrong and worth naming — *populating the token's
(currently empty) `roles` claim at login* would put authority back **into the session**, exactly what
Decision 2 exists to prevent: a stolen or stale token would carry privileges, and a revoked role would
persist until the token expired, breaking **SC-002** ("loses access on their next attempt") and **SC-006**.
Resolving at the edge keeps roles server-side and fresh per request, and requires **no change to any
service's authorization code** — only that `stub` stops meaning "grant everything".

**Alternatives rejected**: *roles in the token at login* (above — fails SC-002/SC-006); *each service
resolves from hr itself* (four new hr clients and four times the traffic; the edge is already the single
place a session is verified — hr remains a special case, resolving its own roles with a local DB read).

**⚠️ Main implementation risk (FR-019)**: the plain `docker-compose.yml` dev stack routes through
`gateway`, which sets **no** `X-Kita-*` headers at all — services there rely on `stub` precisely because
there is no login. Turning `stub` off without a session-bearing entry point makes that stack refuse
everything. Handling, in US4: `stub` is demoted to a **build/test-only seam** (as `InMemoryHrAdapter`
already is); any stack that authorizes must be fronted by the edge; and an `OWNER`-linked account must be
seeded **and verified** before the flip. If fronting the base stack proves disproportionate, the fallback
is to label it explicitly as an unauthenticated development stack rather than quietly leave a permissive
deployed path.

## Decision 8 — the `OWNER` maker-checker exemption needs **no schema change** (FR-020, SC-010)

**Decision**: in `BackOfficePipeline`, the self-review guard (today an unconditional 422 raised *before*
authorization) is skipped when the acting employee holds `OWNER`. Single-person approvals stay
identifiable because `back_office_activity` **already records both** `actor_employee_id` and
`maker_employee_id` — so SC-010 is a query (`WHERE actor_employee_id = maker_employee_id`), not a column.

**Rationale**: the cheapest possible change that satisfies the requirement, and the audit property comes
free from data already captured. **Alternative rejected**: a `single_person_approval` boolean — redundant
with two columns that already exist, and a denormalized flag can disagree with them.

**Recorded trade-off**: this removes segregation of duties on exactly the controls it protects (purchase
approval, payment confirmation, delivery receipt). The user accepted it on 2026-07-29 for single-person
businesses. The mitigation is visibility, not prevention: the activity log must make single-person
approvals easy to list, and the quickstart includes that query.

## Decision 9 — where the `OWNER` branch lives differs by service (found by `/speckit-analyze`, 2026-07-29)

**Decision**: `OWNER`-implies-all is implemented in **`CallerContext.roles()` for hr / crm / procurement**,
but in **`ActionAuthorizer.permits(...)` for workflow-service**.

**Rationale**: workflow does not authorize the way the other three do. Its `CallerContext` says so in its
own javadoc — *"roles are NOT read here — they are resolved from the HR record"* — and the decision is
`ActionAuthorizer.permits(heldRoles, action, kind)`, matching held roles against `authorization_mapping`
rows. **`OWNER` will never appear in that table** (it is seeded per action/role/kind in `V2`), so a branch
placed only in workflow's `CallerContext` would leave an `OWNER` **refused every governed back-office
action** — silently breaking FR-017, US2 scenarios 5 and 7, and making FR-020 unreachable because
`authorize()` fails before the self-review guard is even consulted.

**Why it was nearly missed**: coverage analysis showed the requirement *had* a task. The task existed and
pointed at the wrong component — a class of defect only caught by checking what the named component
actually does. **Alternative rejected**: seeding `OWNER` into `authorization_mapping` for every
action/kind — it would work, but bloats the table, has to be re-seeded whenever an action is added, and
buries a security-critical rule in data instead of stating it once in code.

## Cross-cutting notes

- **No change to authorization rules** (FR-013): `authorization_mapping` and the maker-checker controls
  are untouched; only *whose* roles are read changes.
- **Role tokens** are the existing workflow set (`SALES`, `CASHIER`, `SALES_MANAGER`, `WAREHOUSE_STAFF`,
  `WAREHOUSE_MANAGER`, `PROCUREMENT_STAFF`, `PROCUREMENT_APPROVER`, `PRODUCTION`, `CRM_ADMIN`). HR stores
  them as opaque strings; an unrecognized token grants nothing and must not error (FR-004 scenario 3).
- **Contract tests**: 018's `contractTest` source set already binds workflow's calls to hr's real DTOs —
  adding `roles`/the account link to `EmployeeResponse` will be caught there automatically (SC-003 still
  holds). `HrEmployeeContractTest` must be updated in the same slice.
- **No NEEDS CLARIFICATION remain** — the two material choices were settled with the user on 2026-07-29.
