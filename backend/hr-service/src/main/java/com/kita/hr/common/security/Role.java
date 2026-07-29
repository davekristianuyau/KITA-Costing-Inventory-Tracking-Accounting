package com.kita.hr.common.security;

/** Coarse roles for HR/payroll actions. Real authentication is performed by the gateway. */
public enum Role {
  HR_ADMIN,
  PAYROLL_OFFICER,
  MANAGER,
  EMPLOYEE_SELF,
  /**
   * The highest-position administrator (017 FR-017). Held in the personnel record like any other
   * token, but read by every service as implying all of that service's roles.
   */
  OWNER
}
