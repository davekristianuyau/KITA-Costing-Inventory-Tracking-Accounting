package com.kita.hr.deduction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, UUID> {
  List<Loan> findByEmployeeIdAndStatus(UUID employeeId, LoanStatus status);
}
