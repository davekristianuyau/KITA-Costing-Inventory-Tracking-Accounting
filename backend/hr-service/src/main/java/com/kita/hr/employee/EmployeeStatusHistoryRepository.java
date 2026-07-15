package com.kita.hr.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeStatusHistoryRepository extends JpaRepository<EmployeeStatusHistory, UUID> {

  /**
   * Effective-date order, tie-broken by when the change was recorded — several transitions can share
   * an effective date, and without the tiebreaker their order would be arbitrary.
   */
  List<EmployeeStatusHistory> findByEmployeeIdOrderByEffectiveDateAscChangedAtAsc(UUID employeeId);
}
