package com.kita.hr.employee;

import com.kita.hr.common.AuditWriter;
import com.kita.hr.common.ConflictException;
import com.kita.hr.common.NotFoundException;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Employee master + effective-dated compensation. */
@Service
public class EmployeeService {

  private final EmployeeRepository employees;
  private final EmployeeRoleRepository employeeRoles;
  private final IdentityChangeRepository identityChanges;
  private final CompensationRecordRepository compensations;
  private final EmployeeStatusHistoryRepository statusHistory;
  private final AuditWriter audit;

  public EmployeeService(
      EmployeeRepository employees,
      EmployeeRoleRepository employeeRoles,
      IdentityChangeRepository identityChanges,
      CompensationRecordRepository compensations,
      EmployeeStatusHistoryRepository statusHistory,
      AuditWriter audit) {
    this.employees = employees;
    this.employeeRoles = employeeRoles;
    this.identityChanges = identityChanges;
    this.compensations = compensations;
    this.statusHistory = statusHistory;
    this.audit = audit;
  }

  @Transactional
  public Employee create(CreateEmployeeRequest req, String actor) {
    if (employees.existsByEmployeeNo(req.employeeNo())) {
      throw new ConflictException("employee_no already exists: " + req.employeeNo());
    }
    Employee e = new Employee();
    e.setEmployeeNo(req.employeeNo());
    e.setFirstName(req.firstName());
    e.setLastName(req.lastName());
    e.setBirthDate(req.birthDate());
    e.setEmail(req.email());
    e.setPhone(req.phone());
    e.setEmploymentType(req.employmentType());
    e.setPosition(req.position());
    e.setDateHired(req.dateHired());
    e.setStatus(EmployeeStatus.ACTIVE);
    e.setSssNo(req.sssNo());
    e.setPhilhealthNo(req.philhealthNo());
    e.setPagibigNo(req.pagibigNo());
    e.setTin(req.tin());
    Employee saved = employees.save(e);
    statusHistory.save(
        new EmployeeStatusHistory(
            saved.getId(), null, EmployeeStatus.ACTIVE, req.dateHired(), actor));
    audit.record(actor, "EMPLOYEE_CREATED", saved.getId().toString(), "employee_no=" + req.employeeNo());
    return saved;
  }

  @Transactional(readOnly = true)
  public Employee get(UUID id) {
    return employees.findById(id).orElseThrow(() -> new NotFoundException("employee not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Employee> list() {
    return employees.findAll();
  }

  @Transactional
  public Employee update(UUID id, UpdateEmployeeRequest req, String actor) {
    Employee e = get(id);
    if (req.firstName() != null) {
      e.setFirstName(req.firstName());
    }
    if (req.lastName() != null) {
      e.setLastName(req.lastName());
    }
    if (req.email() != null) {
      e.setEmail(req.email());
    }
    if (req.phone() != null) {
      e.setPhone(req.phone());
    }
    if (req.position() != null) {
      e.setPosition(req.position());
    }
    if (req.sssNo() != null) {
      e.setSssNo(req.sssNo());
    }
    if (req.philhealthNo() != null) {
      e.setPhilhealthNo(req.philhealthNo());
    }
    if (req.pagibigNo() != null) {
      e.setPagibigNo(req.pagibigNo());
    }
    if (req.tin() != null) {
      e.setTin(req.tin());
    }
    if (req.dateSeparated() != null) {
      e.setDateSeparated(req.dateSeparated());
    }
    if (req.status() != null) {
      if (req.status() == EmployeeStatus.SEPARATED
          && req.dateSeparated() == null
          && e.getDateSeparated() == null) {
        throw new ConflictException("separation requires dateSeparated");
      }
      EmployeeStatus previous = e.getStatus();
      if (req.status() != previous) {
        // FR-003: the prior status is retained as history rather than overwritten silently.
        LocalDate effective =
            req.status() == EmployeeStatus.SEPARATED && e.getDateSeparated() != null
                ? e.getDateSeparated()
                : LocalDate.now();
        statusHistory.save(
            new EmployeeStatusHistory(id, previous, req.status(), effective, actor));
      }
      e.setStatus(req.status());
    }
    Employee saved = employees.save(e);
    audit.record(actor, "EMPLOYEE_UPDATED", id.toString(), "status=" + saved.getStatus());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<EmployeeStatusHistory> statusHistory(UUID id) {
    get(id); // 404 if missing
    return statusHistory.findByEmployeeIdOrderByEffectiveDateAscChangedAtAsc(id);
  }

  @Transactional
  public CompensationRecord addCompensation(UUID id, CompensationRequest req, String actor) {
    Employee e = get(id);
    if (compensations.existsByEmployeeIdAndEffectiveDate(id, req.effectiveDate())) {
      throw new ConflictException("compensation already exists for date " + req.effectiveDate());
    }
    CompensationRecord c =
        new CompensationRecord(e, req.effectiveDate(), req.basicPay(), req.payFrequency());
    CompensationRecord saved = compensations.save(c);
    audit.record(actor, "COMPENSATION_ADDED", id.toString(), "effective=" + req.effectiveDate());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<CompensationRecord> listCompensation(UUID id) {
    get(id); // 404 if missing
    return compensations.findByEmployeeIdOrderByEffectiveDateDesc(id);
  }

  /**
   * Resolve a login account to its employee (017 FR-001). Empty means the account has no employee —
   * which the caller must report distinctly from "the employee exists but is inactive" (FR-005).
   */
  public java.util.Optional<Employee> byAccount(String accountUsername) {
    return employees.findByAccountUsername(accountUsername);
  }

  /** The back-office role tokens an employee holds (017 FR-014); opaque strings, never an hr enum. */
  public List<String> rolesOf(UUID employeeId) {
    return employeeRoles.findByEmployeeId(employeeId).stream().map(EmployeeRole::getRole).toList();
  }

  // --- 017 US2: administering the account link and the roles ------------------------------------

  /**
   * Link an account to an employee (FR-002/FR-008/FR-009). Strictly one-to-one in both directions, and
   * a conflict names which side collided so the administrator knows what to unlink. Re-linking is a
   * deliberate unlink-then-link, never a silent overwrite — overwriting would transfer an identity
   * (and its roles) with no trace of the previous holder.
   */
  @Transactional
  public Employee linkAccount(UUID employeeId, String accountUsername, String actor) {
    if (accountUsername == null || accountUsername.isBlank()) {
      throw new ConflictException("an account name is required");
    }
    Employee employee = get(employeeId);
    if (employee.getAccountUsername() != null
        && !employee.getAccountUsername().equals(accountUsername)) {
      throw new ConflictException(
          "employee is already linked to account "
              + employee.getAccountUsername()
              + "; unlink first");
    }
    employees
        .findByAccountUsername(accountUsername)
        .filter(other -> !other.getId().equals(employeeId))
        .ifPresent(
            other -> {
              throw new ConflictException(
                  "account " + accountUsername + " is already linked to another employee");
            });
    employee.setAccountUsername(accountUsername);
    Employee saved = employees.save(employee);
    identityChanges.save(IdentityChange.linked(employeeId, accountUsername, actor));
    return saved;
  }

  /** Unlink; afterwards the account can perform no governed action (FR-008). */
  @Transactional
  public Employee unlinkAccount(UUID employeeId, String actor) {
    Employee employee = get(employeeId);
    String previous = employee.getAccountUsername();
    if (previous == null) {
      throw new ConflictException("employee has no linked account");
    }
    employee.setAccountUsername(null);
    Employee saved = employees.save(employee);
    identityChanges.save(IdentityChange.unlinked(employeeId, previous, actor));
    return saved;
  }

  /** Every employee that currently has a login, for audit: who can act as whom (FR-008). */
  public List<Employee> linkedEmployees() {
    return employees.findAll().stream().filter(e -> e.getAccountUsername() != null).toList();
  }

  /**
   * Replace an employee's roles with exactly this set (FR-014/FR-015). An idempotent full-set replace,
   * so a retried request cannot double-grant or half-apply; every added or removed token is audited, so
   * no privilege change is silent (SC-009). Tokens are opaque — hr never validates them against another
   * service's enum, and an unknown one simply grants nothing downstream.
   */
  @Transactional
  public List<String> replaceRoles(UUID employeeId, List<String> desired, String actor) {
    get(employeeId); // 404 if the employee does not exist
    Set<String> wanted =
        desired == null
            ? Set.of()
            : desired.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(r -> r.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

    List<EmployeeRole> existing = employeeRoles.findByEmployeeId(employeeId);
    Set<String> held =
        existing.stream()
            .map(EmployeeRole::getRole)
            .collect(java.util.stream.Collectors.toSet());

    for (EmployeeRole role : existing) {
      if (!wanted.contains(role.getRole())) {
        employeeRoles.delete(role);
        identityChanges.save(IdentityChange.roleRevoked(employeeId, role.getRole(), actor));
      }
    }
    for (String role : wanted) {
      if (!held.contains(role)) {
        employeeRoles.save(new EmployeeRole(employeeId, role, actor));
        identityChanges.save(IdentityChange.roleGranted(employeeId, role, actor));
      }
    }
    return rolesOf(employeeId);
  }
}
