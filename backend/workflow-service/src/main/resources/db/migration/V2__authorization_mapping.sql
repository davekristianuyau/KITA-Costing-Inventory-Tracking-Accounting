-- Seeded role→action authorization mapping (FR-002, FR-021, SC-004). The maker/checker split lives in
-- `kind`. Loaded into the pure ActionAuthorizer at startup. Roles are the tokens assigned in HR.

CREATE TABLE authorization_mapping (
    id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action TEXT NOT NULL,   -- a BackOfficeAction value
    role   TEXT NOT NULL,   -- a Role token (as assigned in HR)
    kind   TEXT NOT NULL,   -- PERFORM | MAKER | CHECKER
    UNIQUE (action, role, kind)
);

INSERT INTO authorization_mapping (action, role, kind) VALUES
    ('TAKE_SALES_ORDER',         'SALES',                'MAKER'),
    ('CONFIRM_SALES_PAYMENT',    'CASHIER',              'CHECKER'),
    ('CONFIRM_SALES_PAYMENT',    'SALES_MANAGER',        'CHECKER'),
    ('RELEASE_SALES_ORDER',      'WAREHOUSE_STAFF',      'CHECKER'),
    ('RELEASE_SALES_ORDER',      'SALES_MANAGER',        'CHECKER'),
    ('COMPLETE_SALES_ORDER',     'SALES',                'PERFORM'),
    ('COMPLETE_SALES_ORDER',     'CASHIER',              'PERFORM'),
    ('RAISE_PURCHASE_ORDER',     'PROCUREMENT_STAFF',    'PERFORM'),
    ('APPROVE_PURCHASE_ORDER',   'PROCUREMENT_APPROVER', 'PERFORM'),
    ('SEND_PURCHASE_ORDER',      'PROCUREMENT_STAFF',    'PERFORM'),
    ('RECORD_DELIVERY_RECEIPT',  'WAREHOUSE_STAFF',      'MAKER'),
    ('CONFIRM_DELIVERY_RECEIPT', 'WAREHOUSE_MANAGER',    'CHECKER'),
    ('BUILD_PRODUCT',            'PRODUCTION',           'PERFORM'),
    ('MAINTAIN_CUSTOMER',        'CRM_ADMIN',            'PERFORM'),
    ('MAINTAIN_CUSTOMER',        'SALES',                'PERFORM'),
    ('MAINTAIN_SUPPLIER',        'PROCUREMENT_STAFF',    'PERFORM');
