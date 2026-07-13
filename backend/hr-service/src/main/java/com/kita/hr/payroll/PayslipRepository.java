package com.kita.hr.payroll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {
  List<Payslip> findByPayrollRunId(UUID payrollRunId);

  List<Payslip> findByEmployeeId(UUID employeeId);

  void deleteByPayrollRunId(UUID payrollRunId);
}
