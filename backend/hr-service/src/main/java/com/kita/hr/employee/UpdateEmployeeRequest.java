package com.kita.hr.employee;

import java.time.LocalDate;

/** Partial update of an employee; null fields are left unchanged. */
public record UpdateEmployeeRequest(
    String firstName,
    String lastName,
    String email,
    String phone,
    String position,
    EmployeeStatus status,
    LocalDate dateSeparated,
    String sssNo,
    String philhealthNo,
    String pagibigNo,
    String tin) {}
