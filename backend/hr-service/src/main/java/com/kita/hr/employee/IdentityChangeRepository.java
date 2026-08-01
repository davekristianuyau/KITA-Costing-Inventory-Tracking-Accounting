package com.kita.hr.employee;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only audit of link and role changes (017 FR-009, FR-015). */
public interface IdentityChangeRepository extends JpaRepository<IdentityChange, UUID> {

  List<IdentityChange> findByEmployeeIdOrderByChangedAtDesc(UUID employeeId);
}
