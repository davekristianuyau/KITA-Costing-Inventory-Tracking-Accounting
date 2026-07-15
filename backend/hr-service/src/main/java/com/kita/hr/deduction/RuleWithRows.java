package com.kita.hr.deduction;

import java.util.List;

/** A rule paired with its TABLE/BRACKET rows (empty for PERCENT/FIXED). */
public record RuleWithRows(DeductionRule rule, List<DeductionRuleRow> rows) {}
