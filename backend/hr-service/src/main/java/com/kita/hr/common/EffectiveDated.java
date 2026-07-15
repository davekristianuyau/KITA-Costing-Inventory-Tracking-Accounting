package com.kita.hr.common;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A record that takes effect on a date. Used for effective-dated reference data (compensation,
 * deduction/premium rules, holidays) so a payroll run uses the version in effect for its period.
 */
public interface EffectiveDated {

  LocalDate effectiveDate();

  /** The latest record whose effective date is on or before {@code asOf}. */
  static <T extends EffectiveDated> Optional<T> effectiveAsOf(List<T> records, LocalDate asOf) {
    return records.stream()
        .filter(r -> !r.effectiveDate().isAfter(asOf))
        .max(Comparator.comparing(EffectiveDated::effectiveDate));
  }
}
