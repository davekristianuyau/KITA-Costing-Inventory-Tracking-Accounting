package com.kita.hr.common.security;

/** Coarse roles for HR/payroll actions. Real authentication is performed by the gateway. */
public enum Role {
  HR_ADMIN,
  PAYROLL_OFFICER,
  MANAGER,
  EMPLOYEE_SELF
}
