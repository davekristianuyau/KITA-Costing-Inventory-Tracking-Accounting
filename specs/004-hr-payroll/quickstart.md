# Quickstart: Employee HR & Payroll Service

**Feature**: 004-hr-payroll | **Date**: 2026-07-12

Operator happy path once implemented; doubles as the manual acceptance walkthrough. `hr-service` runs
behind the gateway with its own `hr` PostgreSQL schema.

## 1. Onboard an employee
Create an employee with a compensation record (basic pay + frequency) and statutory IDs; add a work
schedule. Expected: unique `employee_no`, status ACTIVE, retrievable with history.

## 2. Load attendance and compute worked time
Post daily time records for the pay period, then request computed worked time. Expected: regular hours,
tardiness/undertime, overtime, holiday, and night hours match the schedule + holiday calendar; missing
attendance is flagged.

## 3. Run payroll
Create a REGULAR run for the pay period + employees → compute → review payslips → finalize. Expected:
each payslip shows itemized earnings, statutory + tax + voluntary deductions, employer contributions,
and `net = gross − deductions`; register totals reconcile to the payslips; a second finalize for the
same period is rejected; employees with invalid compensation are flagged/excluded.

## 4. Deductions
Attach a loan (installment schedule) and confirm one installment is deducted per finalized run until
the balance reaches zero. Verify statutory contributions and withholding tax match the effective PH
seed rules for the period.

## 5. Leave
Define a leave type with accrual, file a request, approve it → balance decreases; file an overlapping
request → rejected; approve UNPAID leave in a period → that period's pay is reduced.

## 6. Outputs
Retrieve a payslip (as the employee), the payroll register, and remittance summaries per agency.
Expected: employee sees only their own payslip; remittance per agency equals the sum across payslips.

## 7. Corrections
Create an ADJUSTMENT run referencing a finalized run; confirm the original stays immutable and the
adjustment carries the delta.

## Acceptance mapping
| Step | Validates |
|------|-----------|
| 1 | US1, FR-001/002/003/004 |
| 2 | US6, FR-025/027/028/029, SC-008 |
| 3 | US2, FR-005–011, SC-001/002/004 |
| 4 | US3, FR-012/013/014/015/016, SC-003/005 |
| 5 | US4, FR-017–020, SC-006 |
| 6 | US5, FR-021/022/024, SC-002/003/007 |
| 7 | FR-011 |
