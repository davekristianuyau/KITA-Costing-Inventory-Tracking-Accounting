package com.kita.hr.deduction;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeductionRuleRepository extends JpaRepository<DeductionRule, UUID> {
  List<DeductionRule> findByEffectiveDateLessThanEqual(LocalDate date);
}
