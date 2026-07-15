-- Philippines statutory discounts as ADOPTABLE SEED DATA (feature 005, FR-012). Nothing
-- jurisdiction-specific lives in code: these are rows a client can use as-is, amend, or replace.
--
-- Values are REPRESENTATIVE and must be reconciled against the governing statutes before production
-- use; adopt a change by inserting a new (code, effective_date) version rather than editing a row.

-- A statutory rule is honoured only for the entitlement kind it belongs to, so SENIOR and PWD do not
-- both fire for one customer.
ALTER TABLE discount_rule ADD COLUMN entitlement_kind TEXT;

-- The VAT rate a VAT_EXEMPT rule strips before applying its percentage.
ALTER TABLE discount_rule ADD COLUMN vat_rate NUMERIC(6, 4);

-- RA 9994 (senior citizens) and RA 10754 (PWD): VAT-exempt sale, then 20% off the VAT-exclusive base.
INSERT INTO discount_rule
    (id, code, origin, computation, value, vat_treatment, vat_rate, entitlement_kind, priority, effective_date)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'PH_SENIOR', 'STATUTORY', 'PERCENT', 0.200000,
     'VAT_EXEMPT', 0.1200, 'SENIOR', 1, '2024-01-01'),
    ('10000000-0000-0000-0000-000000000002', 'PH_PWD', 'STATUTORY', 'PERCENT', 0.200000,
     'VAT_EXEMPT', 0.1200, 'PWD', 1, '2024-01-01');
