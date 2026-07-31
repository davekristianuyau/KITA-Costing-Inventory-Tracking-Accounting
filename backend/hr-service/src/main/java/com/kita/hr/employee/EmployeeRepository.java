package com.kita.hr.employee;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
  Optional<Employee> findByEmployeeNo(String employeeNo);

  boolean existsByEmployeeNo(String employeeNo);

  /** Resolve the employee a login account belongs to (017 FR-001). */
  java.util.Optional<Employee> findByAccountUsername(String accountUsername);
}
