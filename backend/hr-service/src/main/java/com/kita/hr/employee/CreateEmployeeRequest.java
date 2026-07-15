package com.kita.hr.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request to create an employee. */
public record CreateEmployeeRequest(
    @NotBlank String employeeNo,
    @NotBlank String firstName,
    @NotBlank String lastName,
    LocalDate birthDate,
    String email,
    String phone,
    @NotNull EmploymentType employmentType,
    String position,
    @NotNull LocalDate dateHired,
    String sssNo,
    String philhealthNo,
    String pagibigNo,
    String tin) {}
