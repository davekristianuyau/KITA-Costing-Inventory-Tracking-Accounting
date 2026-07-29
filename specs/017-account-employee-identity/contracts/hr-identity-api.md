# Contract: hr-service identity & roles API (017)

New/changed endpoints on **hr-service**. Everything else in the system is unchanged — `identity-service`,
`TokenService` and `edge-gateway` gain nothing (research Decision 1).

Role-gated with hr's existing `CallerContext`: administration requires `HR_ADMIN`; the resolution read is
service-to-service.

## Changed: `GET /api/hr/employees/{id}`

`EmployeeResponse` gains two fields. **This is a consumer-contract change** — workflow's
`contractTest` binds against this record, so it fails the build until the caller is updated (SC-003, by
design).

```
{ id, employeeNo, firstName, lastName, …,
  status,                       // existing: ACTIVE | ON_LEAVE | SUSPENDED | SEPARATED
  accountUsername,              // NEW — the linked login, null when unlinked
  roles: ["SALES","CASHIER"]    // NEW — back-office role tokens, [] when none
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

Link an account to an employee (FR-008). `HR_ADMIN` only (FR-010).

- **Request**: `{ "accountUsername": "alice" }`
- **201/200** → linked; recorded in `account_link_change` with the acting administrator (FR-009).
- **409** → the one-to-one rule is violated: that account is already linked to another employee, or this
  employee already has a different account. Reason names which (FR-002, US2 scenario 3).
- Re-linking is an explicit **unlink then link**, never a silent overwrite.

## New: `DELETE /api/hr/employees/{id}/account`

Unlink (FR-008). `HR_ADMIN` only. Recorded as `UNLINKED` (FR-009). Afterwards the account's governed
actions are refused with the "no employee" reason — distinct from a permission refusal (US2 scenario 2).

## New: `GET /api/hr/account-links`

View current links (FR-008): `[{ employeeId, employeeNo, name, accountUsername, status }]`.
Privileged read (`HR_ADMIN`), so an administrator can audit who can act as whom.

## New: `PUT /api/hr/employees/{id}/roles`

Assign the employee's back-office roles (the storage FR-004 requires). `HR_ADMIN` only.

- **Request**: `{ "roles": ["SALES","CASHIER"] }` — the full desired set (idempotent replace, so a retry
  cannot double-grant).
- Tokens are stored **opaquely**: hr does not validate them against workflow's enum, and an unrecognized
  token must be accepted here and simply grant nothing downstream (FR-004 scenario 3).
- Every change is recorded with the acting administrator — a privilege grant is never silent.

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
