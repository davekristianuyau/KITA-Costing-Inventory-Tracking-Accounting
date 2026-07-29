# Data Model: Account-to-Employee Identity (017)

All new persistence lives in **hr-service** (research Decisions 1 & 2). The **session token is unchanged**;
`edge-gateway` changes only its *source* of roles (Decision 7); `identity-service` gains one rule — account
names are permanent (Decision 3b). No other service gains schema.

## Employee *(existing — extended)*

`hr-service`, table `employee`. Gains the account link.

- **New field**: `account_username` (text, **UNIQUE**, nullable).
  - Nullable because not every employee has a login (spec Assumption).
  - `UNIQUE` enforces one half of FR-002; the other half (an account maps to at most one employee) is
    the same constraint read the other way, since the username is the account's identity within a
    deployment.
- **Unchanged**: `status` (`ACTIVE | ON_LEAVE | SUSPENDED | SEPARATED`) stays the authority on whether
  the person may act — only `ACTIVE` resolves (FR-006).
- **Client scoping**: structural. Each client is an isolated deployment and hr-service has no tenant
  concept, so a link cannot span clients (spec Assumption satisfied without a column).

## EmployeeRole *(new)*

`hr-service`, table `employee_role`. The back-office roles an employee holds — the thing HR did not have.

- **Fields**: `id` (UUID, `@UuidGenerator`), `employee_id` (FK → `employee`, `ON DELETE CASCADE`),
  `role` (text — an opaque token, not an hr enum), `assigned_at`, `assigned_by`.
- **Constraint**: `UNIQUE (employee_id, role)` — a role is held once.
- **Why text, not an enum**: this is **one flat vocabulary** spanning every service's roles — workflow's
  (`SALES`, `PROCUREMENT_APPROVER`, …), hr's own (`HR_ADMIN`, `PAYROLL_OFFICER`, …), crm's, procurement's.
  Each service recognizes its own subset and ignores the rest, so hr never redeploys when another service
  adds a role, and FR-004 scenario 3 (unrecognized token grants nothing, never errors) holds by design.
- **`OWNER`** is an ordinary token in this set with one special reading: **every** service treats it as
  implying all of its own roles (FR-017). It is not a separate flag or table — it is the most powerful
  role, audited like any other grant.
  - ⚠️ **Where that branch lives differs by service.** hr/crm/procurement decide roles in
    `CallerContext.roles()`, so the branch goes there. **workflow-service does not** — its `CallerContext`
    explicitly does not read roles; the decision is `ActionAuthorizer.permits(heldRoles, action, kind)`
    against `authorization_mapping` rows, where `OWNER` will never appear. Putting the branch only in
    workflow's `CallerContext` would leave an `OWNER` **refused every governed action**.
- **Read**: returned on the employee read so one call yields status **and** roles.

## IdentityChange *(new — audit)*

`hr-service`, table `identity_change`. Every link, unlink, **and role grant/revoke** — who did it and when
(FR-009, FR-015). One table, because "who may act as whom, with what privileges" is a single audit story
and reviewers should not have to join two logs to answer it.

- **Fields**: `id` (UUID), `employee_id`, `action`
  (`LINKED | UNLINKED | ROLE_GRANTED | ROLE_REVOKED`), `account_username` (set on link actions, else null),
  `role` (set on role actions, else null), `changed_by` (the acting administrator), `changed_at`.
- **Append-only**, consistent with the project's other audit tables.
- **Resolves an ambiguity found in analysis**: an earlier draft recorded only `LINKED|UNLINKED` and left
  role auditing to "a sibling column set", which left FR-015/SC-009 (every grant attributable, **0** silent
  privilege changes) with nowhere to write. A grant with no audit row is exactly the failure this table
  exists to prevent.
- **SC-009 query** — every privilege change, attributable:
  ```sql
  SELECT changed_at, changed_by, action, role, employee_id FROM identity_change
  WHERE action IN ('ROLE_GRANTED','ROLE_REVOKED') ORDER BY changed_at DESC;
  ```

## Resolution Outcome *(in-flight, not persisted)*

What resolving an account to an actable employee produced — the spec's "Resolution Outcome" entity.

- **Values**: `RESOLVED`, `NO_EMPLOYEE_LINKED`, `EMPLOYEE_NOT_ACTIVE`, `EMPLOYEE_MISSING`, `UNAVAILABLE`.
- **Carries**: the employee id + role set when `RESOLVED`; a specific reason otherwise.
- **Mapping** (SC-004 — none may collapse into "not permitted"):

| Outcome | HTTP | `back_office_activity` outcome |
|---|---|---|
| `RESOLVED` then roles insufficient | 403 | `REJECTED_NOT_PERMITTED` |
| `NO_EMPLOYEE_LINKED` | 422 | `REJECTED_INVALID` (reason: account has no employee) |
| `EMPLOYEE_NOT_ACTIVE` | 422 | `REJECTED_INVALID` (reason names the status) |
| `EMPLOYEE_MISSING` | 422 | `REJECTED_INVALID` (reason: linked employee no longer exists) |
| `UNAVAILABLE` | 503 | `FAILED_UNAVAILABLE` — **fail closed** (FR-011) |

- Replaces today's `Optional<EmployeeView>`, which collapses the middle three into one empty value.

## Single-person approval *(derived — no new column)*

FR-020 lets an `OWNER` be both maker and checker of one action; SC-010 requires those actions be
identifiable afterwards. **No schema change is needed**: `back_office_activity` already stores both
`actor_employee_id` and `maker_employee_id`, so a single-person approval is exactly

```sql
SELECT * FROM back_office_activity
WHERE maker_employee_id IS NOT NULL AND maker_employee_id = actor_employee_id;
```

A denormalized boolean was rejected — it can disagree with the two columns that already answer the question.

## Session Identity *(unchanged shape — new source)*

The header contract is unchanged: `edge-gateway` still strips every inbound `X-Kita-*` and sets trusted
`X-Kita-User` / `X-Kita-Client` / `X-Kita-Roles`. What changes is **where the roles come from**: today the
token's always-empty `roles` claim; after 017, resolved **per request** from the personnel record
(research Decision 7). Roles are therefore never carried as authority *in* the session — they are looked
up beside it, so a revoked role stops working on the next request rather than at token expiry.

Each service's `CallerContext` keeps reading `X-Kita-Roles`; only its **absent-header behaviour** changes,
from "grant everything" (`stub`) to "grant nothing".

The spec's "Session Identity" entity therefore needs no new artifact: the acting identity is already
platform-asserted and unalterable by the client (FR-003). 017 changes only what the receiving side *does*
with it — hr resolves it to an employee instead of workflow assuming it already is one.

## Persistence changes

**Two new tables in hr-service** (`employee_role`, `identity_change`) plus **one column** on
`employee` (`account_username`), delivered as a single Flyway migration (next free version — `V11`,
after 018's `V10__service_call_refusal.sql`). **No schema change in identity-service, workflow-service,
or any other service.** `workflow-service` persists nothing new; its `back_office_activity` gains no
columns, only new reason strings (FR-013: the rules don't change, only whose roles are read).
