package com.kita.hr.employee;

import java.util.List;

/**
 * The full desired role set for an employee (017 FR-014). A replace, not a delta, so a retried request
 * cannot double-grant. Tokens are opaque strings — hr validates them against no enum.
 */
public record EmployeeRolesRequest(List<String> roles) {}
