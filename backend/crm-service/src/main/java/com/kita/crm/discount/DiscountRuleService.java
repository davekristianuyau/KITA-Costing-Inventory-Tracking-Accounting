package com.kita.crm.discount;

import com.kita.crm.common.AuditWriter;
import com.kita.crm.common.ConflictException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages discount rules and the stacking policy. Every change is audited (FR-016). */
@Service
public class DiscountRuleService {

  private final DiscountRuleRepository rules;
  private final StackingPolicyRepository policies;
  private final AuditWriter audit;

  public DiscountRuleService(
      DiscountRuleRepository rules, StackingPolicyRepository policies, AuditWriter audit) {
    this.rules = rules;
    this.policies = policies;
    this.audit = audit;
  }

  @Transactional
  public DiscountRule create(DiscountRule rule, String actor) {
    boolean duplicate =
        rules.findByEffectiveDateLessThanEqual(rule.effectiveDate()).stream()
            .anyMatch(
                r ->
                    r.getCode().equals(rule.getCode())
                        && r.effectiveDate().equals(rule.effectiveDate()));
    if (duplicate) {
      throw new ConflictException(
          "rule " + rule.getCode() + " already has a version effective " + rule.effectiveDate());
    }
    DiscountRule saved = rules.save(rule);
    audit.record(
        actor,
        "RULE_CHANGED",
        saved.getId().toString(),
        "code=" + rule.getCode() + " effective=" + rule.effectiveDate());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<DiscountRule> listEffective(LocalDate asOf) {
    return rules.findByEffectiveDateLessThanEqual(asOf);
  }

  @Transactional(readOnly = true)
  public StackingPolicy activePolicy() {
    return policies.findAllByOrderByUpdatedAtDesc().stream()
        .findFirst()
        .orElseGet(() -> new StackingPolicy(StackingMode.MOST_FAVORABLE, "default"));
  }

  @Transactional
  public StackingPolicy setPolicy(StackingMode mode, String actor) {
    StackingPolicy current = activePolicy();
    current.setMode(mode);
    current.setUpdatedBy(actor);
    StackingPolicy saved = policies.save(current);
    audit.record(actor, "POLICY_CHANGED", saved.getId().toString(), "mode=" + mode);
    return saved;
  }
}
