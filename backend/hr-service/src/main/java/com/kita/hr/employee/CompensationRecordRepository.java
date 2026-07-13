package com.kita.hr.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecord, UUID> {
  List<CompensationRecord> findByEmployeeIdOrderByEffectiveDateDesc(UUID employeeId);

  boolean existsByEmployeeIdAndEffectiveDate(UUID employeeId, java.time.LocalDate effectiveDate);
}
