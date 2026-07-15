package com.kita.hr.payroll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayComponentRepository extends JpaRepository<PayComponent, UUID> {
  List<PayComponent> findByPayslipId(UUID payslipId);
}
