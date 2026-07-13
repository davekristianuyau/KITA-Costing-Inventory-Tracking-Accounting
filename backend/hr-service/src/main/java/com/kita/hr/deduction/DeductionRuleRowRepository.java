package com.kita.hr.deduction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeductionRuleRowRepository extends JpaRepository<DeductionRuleRow, UUID> {
  List<DeductionRuleRow> findByRuleId(UUID ruleId);
}
