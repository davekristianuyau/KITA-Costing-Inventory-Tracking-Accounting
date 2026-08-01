package com.kita.hr.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The back-office roles an employee holds (017 FR-014). */
public interface EmployeeRoleRepository extends JpaRepository<EmployeeRole, UUID> {

  List<EmployeeRole> findByEmployeeId(UUID employeeId);

  void deleteByEmployeeId(UUID employeeId);
}
