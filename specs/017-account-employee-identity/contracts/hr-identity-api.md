# Contract: hr-service identity & roles API (017)

New/changed endpoints on **hr-service**, which owns the link and the roles (research Decision 1).

Elsewhere: the **session token is unchanged** (its `roles` claim stays unused — deliberately, see
Decision 7); **`edge-gateway` changes only its source of roles** (resolved per request instead of read
from an empty claim); **identity-service** gains one rule, that account names are permanent and never
reissued (Decision 3b). No new claim, no new header.

Role-gated with hr's existing `CallerContext`: **link and role administration require `OWNER`** (FR-017);
the resolution read is service-to-service.

## Changed: `GET /api/hr/employees/{id}`

`EmployeeResponse` gains two fields. **This is a consumer-contract change** — workflow's
`contractTest` binds against this record, so it fails the build until the caller is updated (SC-003, by
design).

```
{ id, employeeNo, firstName, lastName, …,
  status,                       // existing: ACTIVE | ON_LEAVE | SUSPENDED | SEPARATED
  accountUsername,              // NEW — the linked login, null when unlinked
  roles: ["SALES","OWNER"]      // NEW — role tokens for ALL services (opaque), [] when none
}
```

## New: `GET /api/hr/employees/by-account/{username}`

The resolution endpoint `workflow-service` calls on every governed action.

- **200** → the same `EmployeeResponse` (status + roles + link).
- **404** → no employee is linked to that account. The caller maps this to `NO_EMPLOYEE_LINKED` — which
  must stay distinguishable from "employee exists but is inactive" (200 with a non-ACTIVE `status`) and
  from a permission refusal (FR-005, SC-004).
- **5xx / unreachable** → the caller fails **closed** with `UNAVAILABLE` → 503, never granting access
  (FR-011).

Deliberately one call returning status **and** roles, so authorization needs a single round trip.

## New: `PUT /api/hr/employees/{id}/account`

Link an account to an employee (FR-008). **`OWNER` only** (FR-010/FR-017) — linking decides *who a login
is*, so it carries the same weight as granting roles.

The account name is treated as **permanent**: identity-service must not rename an account, and must never
reissue a deactivated account's name (FR-016). Without that rule a rename or reuse silently transfers this
employee's identity and roles to another person.

- **Request**: `{ "accountUsername": "alice" }`
- **201/200** → linked; recorded in `account_link_change` with the acting administrator (FR-009).
- **409** → the one-to-one rule is violated: that account is already linked to another employee, or this
  employee already has a different account. Reason names which (FR-002, US2 scenario 3).
- Re-linking is an explicit **unlink then link**, never a silent overwrite.

## New: `DELETE /api/hr/employees/{id}/account`

Unlink (FR-008). **`OWNER` only**. Recorded as `UNLINKED` (FR-009). Afterwards the account's governed
actions are refused with the "no employee" reason — distinct from a permission refusal (US2 scenario 2).

## New: `GET /api/hr/account-links`

View current links (FR-008): `[{ employeeId, employeeNo, name, accountUsername, status }]`.
Privileged read (`OWNER` or `HR_ADMIN`), so an administrator can audit who can act as whom.

## New: `PUT /api/hr/employees/{id}/roles`

Assign the employee's roles (the storage FR-004 requires). **`OWNER` only** — granting privileges is the
most powerful operation in the system, so it sits with the highest-position administrator (FR-017).

- **Request**: `{ "roles": ["SALES","CASHIER"] }` — the full desired set (idempotent replace, so a retry
  cannot double-grant).
- **One flat vocabulary**: these tokens span every service — workflow's (`SALES`, `PROCUREMENT_APPROVER`,
  …), hr's own (`HR_ADMIN`, `PAYROLL_OFFICER`, …), crm's, procurement's, plus **`OWNER`**.
- Tokens are stored **opaquely**: hr validates nothing against another service's enum, and an unrecognized
  token must be accepted here and simply grant nothing downstream (FR-004 scenario 3).
- Every change is recorded with the acting administrator (FR-015) — a privilege grant is never silent.

## Role semantics every service must implement

- A service recognizes the subset of tokens it knows and **ignores the rest** (never errors).
- **`OWNER` implies all of that service's own roles** — one "if OWNER, grant" branch per `CallerContext`.
- **Absent role header now grants nothing.** Today a caller with no `X-Kita-Roles` is treated as fully
  privileged (`stub: true`); after 017 that fallback exists only for isolated `:service:test` runs, never
  in a stack that authorizes (FR-018, SC-008).

## Changed: how `X-Kita-Roles` is populated (edge-gateway)

The header contract is unchanged; its **source** changes. `edge-gateway` resolves account → employee →
roles from hr **per request** (it already validates the session per request) and sets the trusted header.

- Roles are **never** written into the session token. Putting them there would let a stolen or stale token
  carry privileges and delay revocation until expiry — breaking SC-002/SC-006 (research Decision 7).
- hr unreachable ⇒ **fail closed**: the request is refused as temporarily unavailable, never granted
  (FR-011).
- An account that resolves to no employee, or to an inactive one, yields **no roles** — the request then
  fails the receiving service's own authorization, distinctly from a business rejection.

## Changed: `BackOfficePipeline` — the `OWNER` maker-checker exemption

The self-review guard (today an unconditional 422 raised *before* authorization) is **skipped when the
acting employee holds `OWNER`** (FR-020). Everyone else is still refused for reviewing their own work.

No schema change: `back_office_activity` already records `actor_employee_id` **and**
`maker_employee_id`, so single-person approvals are listable (SC-010) — see
[data-model.md](../data-model.md).

## Caller contract (`workflow-service`)

`HrPort.getEmployee(actor)` becomes a **resolution**, not an `Optional`:

```
ResolutionOutcome resolve(String accountUsername)
  → RESOLVED(employeeId, roles) | NO_EMPLOYEE_LINKED | EMPLOYEE_NOT_ACTIVE
  | EMPLOYEE_MISSING | UNAVAILABLE
```

- Lookup key is the **account username** (what `X-Kita-User` carries), not an HR UUID — that mismatch is
  the bug 017 exists to fix.
- `ActorResolver` maps each outcome per [data-model.md](../data-model.md); none may become 403.
- Activity is attributed to the **resolved employee**, so an action records who really did it (SC-003).
- **Not cached** — caching reintroduces the revocation delay this design exists to remove (SC-002).

## Removed (FR-012)

- `workflow.hr.position-roles` + `HrPositionRoles` (018's stand-in).
- `DemoSeeder`'s `emp-*` magic-name logins → real employees with real links.
- `InMemoryHrAdapter` **stays** as a unit-test seam, held to this contract by the `contractTest` suite —
  it is no longer a deployed path (`HR_ADAPTER=http` everywhere, including 016's console default).
