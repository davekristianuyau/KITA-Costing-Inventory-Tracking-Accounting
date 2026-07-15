# Contract: Statutory / Deduction Rule Engine

**Feature**: 004-hr-payroll | **Date**: 2026-07-12

A **generic, effective-dated** engine (Q2=C). No jurisdiction is hard-coded; the Philippines ruleset
ships as **seed data** a client can adopt or replace (FR-013/016/026).

## Rule shape

A `DeductionRule` has: `code`, `kind` (STATUTORY | TAX | VOLUNTARY_TEMPLATE), `agency` (for
remittance), `base` (GROSS | BASIC | TAXABLE_INCOME | CUSTOM), `computation`, `effective_date`, and a
payload:

- **TABLE**: rows of `[range_low, range_high] → employee_amount, employer_amount` (e.g., SSS).
- **BRACKET**: rows of `[income_low, income_high] → base_tax, rate_on_excess, excess_over` (e.g., BIR
  withholding).
- **PERCENT**: `rate` applied to `base` (with optional floor/cap) (e.g., PhilHealth share).
- **FIXED**: a flat amount.

## Evaluation

1. Select rules whose `effective_date` ≤ pay period start and are the latest version per `code`.
2. Evaluate in the order defined by `payroll-computation.md` (pre-tax statutory before tax so the
   taxable base is correct).
3. Emit one PayComponent per rule (employee side) and one EMPLOYER_CONTRIB per rule that has an
   employer amount.

## Philippines seed (adoptable; delivered as a Flyway seed migration)

- **SSS** — TABLE by monthly salary credit → employee + employer contribution.
- **PhilHealth** — PERCENT of basic within a floor/cap, split employee/employer.
- **Pag-IBIG (HDMF)** — PERCENT/FIXED with a cap, employee + employer.
- **BIR withholding tax** — BRACKET on taxable income per the effective tax table + pay frequency.

Seed rules are ordinary data rows; updating a government table = inserting a new effective-dated
version, no code change (SC-003). Non-PH clients add their own rules the same way.

## Tests

- Golden-value tests per seeded rule (known salary → known contribution/tax) — SC-003.
- Effective-dating test: a period before/after a rule version boundary uses the correct version.
- Base-ordering test: pre-tax statutory reduces the taxable base before withholding tax.
