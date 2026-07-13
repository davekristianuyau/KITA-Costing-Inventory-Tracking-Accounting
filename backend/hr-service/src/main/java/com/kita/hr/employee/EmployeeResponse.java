package com.kita.hr.employee;

import java.time.LocalDate;
import java.util.UUID;

/** Employee view returned by the API. */
public record EmployeeResponse(
    UUID id,
    String employeeNo,
    String firstName,
    String lastName,
    LocalDate birthDate,
    String email,
    String phone,
    EmploymentType employmentType,
    String position,
    LocalDate dateHired,
    LocalDate dateSeparated,
    EmployeeStatus status,
    String sssNo,
    String philhealthNo,
    String pagibigNo,
    String tin) {

  public static EmployeeResponse from(Employee e) {
    return new EmployeeResponse(
        e.getId(),
        e.getEmployeeNo(),
        e.getFirstName(),
        e.getLastName(),
        e.getBirthDate(),
        e.getEmail(),
        e.getPhone(),
        e.getEmploymentType(),
        e.getPosition(),
        e.getDateHired(),
        e.getDateSeparated(),
        e.getStatus(),
        e.getSssNo(),
        e.getPhilhealthNo(),
        e.getPagibigNo(),
        e.getTin());
  }
}
