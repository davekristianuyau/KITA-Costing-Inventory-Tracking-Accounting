-- 017: the personnel record becomes the source of identity AND of back-office roles.
--
-- Two gaps this closes, both verified in the code before writing:
--   * nothing connected a login account to an employee (the console sent a login name, workflow looked
--     employees up by UUID; the demo only worked because seeded logins were named after fake employees);
--   * hr stored NO back-office roles at all — hr's own Role enum governs hr's own API, not what someone
--     may do in the back office. FR-004 was unachievable without this table.

-- The account this employee signs in as. NULL when they have no login (service/admin accounts, or staff
-- who simply do not use the system). UNIQUE gives FR-002's one-to-one in both directions: one account
-- maps to at most one employee, and one employee row can name at most one account.
ALTER TABLE employee ADD COLUMN account_username TEXT;
ALTER TABLE employee ADD CONSTRAINT uq_employee_account_username UNIQUE (account_username);

-- Roles are OPAQUE strings spanning every service's vocabulary (workflow's SALES/PROCUREMENT_APPROVER…,
-- hr's HR_ADMIN…, crm's, procurement's, plus OWNER). hr deliberately does not validate them against any
-- enum: a service must be able to add a role without an hr redeploy, and an unrecognized token must
-- simply grant nothing rather than error (FR-004).
CREATE TABLE employee_role (
    id          UUID PRIMARY KEY,
    employee_id UUID        NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by TEXT        NOT NULL,          -- the administrator who granted it
    CONSTRAINT uq_employee_role UNIQUE (employee_id, role)
);

CREATE INDEX idx_employee_role_employee ON employee_role (employee_id);

-- Append-only audit of who may act as whom, and with what privileges (FR-009, FR-015).
-- Link changes AND role changes share one table on purpose: "who can do what, and who decided that"
-- is a single question, and a reviewer should not have to join two logs to answer it.
CREATE TABLE identity_change (
    id               UUID PRIMARY KEY,
    employee_id      UUID        NOT NULL REFERENCES employee (id),
    action           TEXT        NOT NULL,     -- LINKED | UNLINKED | ROLE_GRANTED | ROLE_REVOKED
    account_username TEXT,                     -- set on LINKED/UNLINKED
    role             TEXT,                     -- set on ROLE_GRANTED/ROLE_REVOKED
    changed_by       TEXT        NOT NULL,     -- the acting administrator; never null, never silent
    changed_at       TIMESTAMPTZ NOT NULL
);

-- Read newest-first when investigating, and by employee when auditing one person.
CREATE INDEX idx_identity_change_changed_at ON identity_change (changed_at DESC);
CREATE INDEX idx_identity_change_employee ON identity_change (employee_id);
