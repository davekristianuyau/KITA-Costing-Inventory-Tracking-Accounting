package com.kita.hr.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.hr.support.AbstractHrIT;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** FR-003: status changes are retained as effective-dated history, never destructively overwritten. */
class EmployeeStatusHistoryIT extends AbstractHrIT {

  @Autowired private EmployeeService employees;

  private UUID employee(String no) {
    return employees
        .create(
            new CreateEmployeeRequest(
                no, "St", "At", null, null, null, EmploymentType.REGULAR, null,
                LocalDate.of(2025, 1, 1), null, null, null, null),
            "hr")
        .getId();
  }

  @Test
  void hireRecordsTheInitialActiveStatus() {
    UUID id = employee("SH-1");

    List<EmployeeStatusHistory> history = employees.statusHistory(id);
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getPreviousStatus()).isNull();
    assertThat(history.get(0).getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    assertThat(history.get(0).getEffectiveDate()).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  void eachStatusTransitionIsRetainedWithItsPredecessor() {
    UUID id = employee("SH-2");

    employees.update(id, update(EmployeeStatus.SUSPENDED, null), "hr");
    employees.update(id, update(EmployeeStatus.ACTIVE, null), "hr");
    // Separation dated in the future so it sorts last; a backdated separation would legitimately
    // sort earlier, since this history is ordered by when a status took effect.
    employees.update(id, update(EmployeeStatus.SEPARATED, LocalDate.of(2026, 12, 31)), "hr");

    List<EmployeeStatusHistory> history = employees.statusHistory(id);
    assertThat(history).hasSize(4); // ACTIVE (hire) + 3 transitions
    assertThat(history)
        .extracting(EmployeeStatusHistory::getStatus)
        .containsExactly(
            EmployeeStatus.ACTIVE,
            EmployeeStatus.SUSPENDED,
            EmployeeStatus.ACTIVE,
            EmployeeStatus.SEPARATED);

    EmployeeStatusHistory separation = history.get(3);
    assertThat(separation.getPreviousStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    // A separation is effective on the separation date, not on the day it was keyed in.
    assertThat(separation.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(employees.get(id).getStatus()).isEqualTo(EmployeeStatus.SEPARATED);
  }

  @Test
  void reassertingTheSameStatusAddsNoHistory() {
    UUID id = employee("SH-3");
    employees.update(id, update(EmployeeStatus.ACTIVE, null), "hr");
    assertThat(employees.statusHistory(id)).hasSize(1);
  }

  private UpdateEmployeeRequest update(EmployeeStatus status, LocalDate separated) {
    return new UpdateEmployeeRequest(
        null, null, null, null, null, status, separated, null, null, null, null);
  }
}
