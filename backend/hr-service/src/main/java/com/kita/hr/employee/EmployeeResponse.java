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
    String tin,
    /** The login this employee signs in as; null when unlinked (017 FR-001). */
    String accountUsername,
    /** Role tokens held in the personnel record — the source of truth for authorization (FR-004). */
    java.util.List<String> roles) {

  /** Without roles — for callers that only need the person, not what they may do. */
  public static EmployeeResponse from(Employee e) {
    return from(e, java.util.List.of());
  }

  public static EmployeeResponse from(Employee e, java.util.List<String> roles) {
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
        LogScrubber.mask(e.getTin()),
        e.getAccountUsername(),
        roles == null ? java.util.List.of() : java.util.List.copyOf(roles));
  }
}
