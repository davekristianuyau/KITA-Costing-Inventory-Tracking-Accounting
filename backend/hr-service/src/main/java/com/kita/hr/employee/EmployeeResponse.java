package com.kita.hr.employee;

import com.kita.hr.common.LogScrubber;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Employee view returned by the API. Statutory and tax identifiers are masked to a last-four hint —
 * they are stored for deductions and remittance but never handed back in the clear (FR-004).
 */
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
        LogScrubber.mask(e.getSssNo()),
        LogScrubber.mask(e.getPhilhealthNo()),
        LogScrubber.mask(e.getPagibigNo()),
        LogScrubber.mask(e.getTin()));
  }
}
