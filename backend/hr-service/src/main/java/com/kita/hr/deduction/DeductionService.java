package com.kita.hr.deduction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the effective deduction ruleset and computes statutory + tax deductions for a payslip. */
@Service
public class DeductionService {

  private final DeductionRuleRepository rules;
  private final DeductionRuleRowRepository rows;

  public DeductionService(DeductionRuleRepository rules, DeductionRuleRowRepository rows) {
    this.rules = rules;
    this.rows = rows;
  }

  /** Create a new effective-dated rule version, with optional TABLE/BRACKET rows. */
  @Transactional
  public DeductionRule createRule(DeductionRule rule, List<DeductionRuleRow> ruleRows) {
    DeductionRule saved = rules.save(rule);
    for (DeductionRuleRow row : ruleRows) {
      rows.save(
          new DeductionRuleRow(
              saved.getId(),
              row.getLow(),
              row.getHigh(),
              row.getEmployeeAmount(),
              row.getEmployerAmount(),
              row.getBaseTax(),
              row.getRateOnExcess(),
              row.getExcessOver()));
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public DeductionCalculator.Outcome compute(BigDecimal gross, BigDecimal basic, LocalDate asOf) {
    List<RuleWithRows> statutory = new ArrayList<>();
    List<RuleWithRows> tax = new ArrayList<>();
    for (DeductionRule rule : effectiveRules(asOf)) {
      RuleWithRows rw = new RuleWithRows(rule, rows.findByRuleId(rule.getId()));
      if (rule.getKind() == DeductionKind.STATUTORY) {
        statutory.add(rw);
      } else if (rule.getKind() == DeductionKind.TAX) {
        tax.add(rw);
      }
    }
    return DeductionCalculator.compute(statutory, tax, gross, basic);
  }

  /** The latest version of each rule code effective on or before {@code asOf}. */
  @Transactional(readOnly = true)
  public List<DeductionRule> effectiveRules(LocalDate asOf) {
    Map<String, DeductionRule> latest = new LinkedHashMap<>();
    for (DeductionRule r : rules.findByEffectiveDateLessThanEqual(asOf)) {
      DeductionRule cur = latest.get(r.getCode());
      if (cur == null || r.effectiveDate().isAfter(cur.effectiveDate())) {
        latest.put(r.getCode(), r);
      }
    }
    List<DeductionRule> out = new ArrayList<>(latest.values());
    out.sort(Comparator.comparing(DeductionRule::getCode));
    return out;
  }
}
