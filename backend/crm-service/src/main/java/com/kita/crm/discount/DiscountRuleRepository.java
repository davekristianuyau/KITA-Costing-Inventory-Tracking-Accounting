package com.kita.crm.discount;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the loyalty module resolves a tier's discount rule. */
public interface DiscountRuleRepository extends JpaRepository<DiscountRule, UUID> {
  List<DiscountRule> findByEffectiveDateLessThanEqual(LocalDate date);
}
