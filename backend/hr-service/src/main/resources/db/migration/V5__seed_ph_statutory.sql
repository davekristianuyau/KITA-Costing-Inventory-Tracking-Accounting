-- Philippines statutory ruleset as ADOPTABLE SEED DATA (feature 004, Q2=C). Not hardwired in code.
-- Values here are REPRESENTATIVE and must be reconciled against the official SSS/PhilHealth/HDMF/BIR
-- tables before production use; update by inserting a new (code, effective_date) version.

-- SSS (TABLE by monthly salary credit) — employee + employer contribution.
INSERT INTO deduction_rule (id, code, kind, computation, base, agency, effective_date) VALUES
  ('10000000-0000-0000-0000-000000000001', 'SSS', 'STATUTORY', 'TABLE', 'GROSS', 'SSS', '2024-01-01');
INSERT INTO deduction_rule_row (id, rule_id, low, high, employee_amount, employer_amount) VALUES
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 0, 19999.99, 900.00, 1900.00),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 20000.00, 29999.99, 1350.00, 2850.00),
  ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 30000.00, NULL, 1350.00, 2850.00);

-- PhilHealth (PERCENT of basic within floor/cap), split employee/employer.
INSERT INTO deduction_rule (id, code, kind, computation, base, agency, rate, employer_rate, floor, cap, effective_date) VALUES
  ('10000000-0000-0000-0000-000000000002', 'PHILHEALTH', 'STATUTORY', 'PERCENT', 'BASIC', 'PhilHealth', 0.025000, 0.025000, 10000.00, 100000.00, '2024-01-01');

-- Pag-IBIG / HDMF (PERCENT of basic, base capped at 10,000 → max 200).
INSERT INTO deduction_rule (id, code, kind, computation, base, agency, rate, employer_rate, cap, effective_date) VALUES
  ('10000000-0000-0000-0000-000000000003', 'PAGIBIG', 'STATUTORY', 'PERCENT', 'BASIC', 'Pag-IBIG', 0.020000, 0.020000, 10000.00, '2024-01-01');

-- BIR withholding tax (BRACKET on monthly taxable income) — representative TRAIN-style monthly table.
INSERT INTO deduction_rule (id, code, kind, computation, base, agency, effective_date) VALUES
  ('10000000-0000-0000-0000-000000000004', 'BIR_WHT', 'TAX', 'BRACKET', 'TAXABLE_INCOME', 'BIR', '2024-01-01');
INSERT INTO deduction_rule_row (id, rule_id, low, high, base_tax, rate_on_excess, excess_over) VALUES
  ('30000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004', 0, 20833.00, 0.00, 0.000000, 0.00),
  ('30000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000004', 20833.01, 33332.00, 0.00, 0.150000, 20833.00),
  ('30000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', 33332.01, 66666.00, 1875.00, 0.200000, 33333.00),
  ('30000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 66666.01, NULL, 8541.80, 0.250000, 66667.00);
