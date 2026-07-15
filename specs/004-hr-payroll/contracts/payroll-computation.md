# Contract: Payroll Computation

**Feature**: 004-hr-payroll | **Date**: 2026-07-12

Defines how a payslip is computed for one employee in a run. All math is exact decimal
(`NUMERIC`/`BigDecimal`), rounded half-up to the currency minor unit **per line**; totals are the sum
of rounded lines so payslip ↔ register reconcile to the cent (FR-006, SC-002).

## Inputs (as of the pay period)

- Effective `CompensationRecord` (basic pay, frequency, allowances).
- `WorkedTime` for the period (regular/OT/tardiness/undertime/absence/night/holiday hours).
- Effective `PremiumRule`s and `HolidayCalendar`.
- Effective `DeductionRule`s (statutory + tax); active `Loan`s; approved leave for the period.

## Gross earnings

1. **Basic**: pro-rate basic pay for days active in the period (hire/separation aware, FR-007).
2. **Allowances**: add recurring allowances (flag taxable vs non-taxable).
3. **Premiums** (US6): overtime = OT hours × hourly rate × OVERTIME multiplier; holiday pay = holiday
   hours × rate × the day's multiplier; night differential = night hours × rate × NIGHT_DIFF
   multiplier. Hourly rate derived from basic pay and the schedule's standard hours.
4. **Leave impact**: approved UNPAID leave days reduce basic for those days (FR-020); PAID leave no
   change; CONVERTIBLE handled per policy at period/'year end.

`gross = basic(pro-rated, less unpaid leave) + allowances + premiums`

## Deductions (order matters for tax base)

1. **Pre-tax statutory** (employee share): SSS/PhilHealth/Pag-IBIG-type contributions from the
   effective `DeductionRule` (TABLE/BRACKET on the defined base).
2. **Taxable income** = gross − non-taxable allowances − pre-tax statutory deductions.
3. **Withholding tax**: BIR-type BRACKET rule applied to taxable income.
4. **Voluntary**: loan installment (one per finalized run until settled, FR-012/SC-005) and other
   salary deductions.

`total_deductions = statutory + tax + voluntary`
`net_pay = gross − total_deductions`

## Employer contributions

Computed from the same statutory rules (employer share); recorded as EMPLOYER_CONTRIB PayComponents,
**excluded** from `net_pay`, included in remittance summaries (FR-014).

## Guards

- If `net_pay` < configured floor (or deductions exceed a max), the employee is **flagged for review**;
  no negative/below-floor payslip is produced (FR-015, SC-004).
- Employee with missing/invalid compensation or incomplete attendance → flagged and excluded (FR-010).
- Finalize is atomic + idempotent on (client, period, employee-set); re-finalize rejected (FR-009).

## Reconciliation

Register column totals = Σ payslip lines for that column, to the cent (SC-002). Remittance per agency =
Σ that statutory/tax component across payslips in the run (SC-003 / FR-022).
