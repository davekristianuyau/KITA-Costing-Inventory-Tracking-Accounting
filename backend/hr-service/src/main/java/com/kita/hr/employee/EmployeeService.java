package com.kita.hr.employee;

import com.kita.hr.common.AuditWriter;
import com.kita.hr.common.ConflictException;
import com.kita.hr.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Employee master + effective-dated compensation. */
@Service
public class EmployeeService {

  private final EmployeeRepository employees;
  private final CompensationRecordRepository compensations;
  private final AuditWriter audit;

  public EmployeeService(
      EmployeeRepository employees,
      CompensationRecordRepository compensations,
      AuditWriter audit) {
    this.employees = employees;
    this.compensations = compensations;
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
    if (req.firstName() != null) e.setFirstName(req.firstName());
    if (req.lastName() != null) e.setLastName(req.lastName());
    if (req.email() != null) e.setEmail(req.email());
    if (req.phone() != null) e.setPhone(req.phone());
    if (req.position() != null) e.setPosition(req.position());
    if (req.sssNo() != null) e.setSssNo(req.sssNo());
    if (req.philhealthNo() != null) e.setPhilhealthNo(req.philhealthNo());
    if (req.pagibigNo() != null) e.setPagibigNo(req.pagibigNo());
    if (req.tin() != null) e.setTin(req.tin());
    if (req.dateSeparated() != null) e.setDateSeparated(req.dateSeparated());
    if (req.status() != null) {
      if (req.status() == EmployeeStatus.SEPARATED
          && req.dateSeparated() == null
          && e.getDateSeparated() == null) {
        throw new ConflictException("separation requires dateSeparated");
      }
      e.setStatus(req.status());
    }
    Employee saved = employees.save(e);
    audit.record(actor, "EMPLOYEE_UPDATED", id.toString(), "status=" + saved.getStatus());
    return saved;
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
}
