# Data Model: Account-to-Employee Identity (017)

All new persistence lives in **hr-service** (research Decisions 1 & 2). `identity-service`, the session
token and `edge-gateway` are **unchanged**.

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
- **Why text, not an enum**: these are *workflow's* role tokens (`SALES`, `PROCUREMENT_APPROVER`, …), not
  hr's own `Role` enum. Storing them opaquely keeps hr from having to redeploy when the back office adds
  a role, and satisfies FR-004 scenario 3 (an unrecognized token grants nothing and must not error).
- **Read**: returned on the employee read so one call yields status **and** roles.

## AccountLinkChange *(new — audit)*

`hr-service`, table `account_link_change`. Every link/unlink, who did it and when (FR-009).

- **Fields**: `id` (UUID), `employee_id`, `account_username`, `action` (`LINKED | UNLINKED`),
  `changed_by` (the acting administrator), `changed_at`.
- **Append-only**, consistent with the project's other audit tables. Role assignment changes are recorded
  here too (or in a sibling column set) so a privilege grant is never silent.

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

## Session Identity *(unchanged — already satisfied)*

The spec's "Session Identity" entity needs no new artifact. `edge-gateway` **strips every inbound
`X-Kita-*`** and sets `X-Kita-User` from the validated session subject, so the acting identity is already
platform-asserted and unalterable by the client (FR-003). 017 changes only what the receiving side *does*
with it: hr resolves it to an employee instead of workflow assuming it already is one.

## Persistence changes

**Two new tables in hr-service** (`employee_role`, `account_link_change`) plus **one column** on
`employee` (`account_username`), delivered as a single Flyway migration (next free version — `V11`,
after 018's `V10__service_call_refusal.sql`). **No schema change in identity-service, workflow-service,
or any other service.** `workflow-service` persists nothing new; its `back_office_activity` gains no
columns, only new reason strings (FR-013: the rules don't change, only whose roles are read).
