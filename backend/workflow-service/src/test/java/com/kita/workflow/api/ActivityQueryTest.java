package com.kita.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kita.workflow.activity.ActivityOutcome;
import com.kita.workflow.activity.ActivityRecord;
import com.kita.workflow.activity.ActivityRecordRepository;
import com.kita.workflow.authorization.BackOfficeAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Read-side filtering of the activity log (FR-003, feature 016). The outcome filter is additive: it
 * composes with the existing filters and changes nothing when absent. Reading MUST record nothing
 * (FR-012) — a read that writes would silently pollute the audit trail.
 */
class ActivityQueryTest {

  private final ActivityRecordRepository repository = mock(ActivityRecordRepository.class);
  private final ActivityController controller = new ActivityController(repository);

  /** A row as the repository returns it — persisted, so it has an id and a timestamp. */
  private static ActivityRecord record(
      String actor, BackOfficeAction action, ActivityOutcome outcome) {
    ActivityRecord row = mock(ActivityRecord.class);
    when(row.getId()).thenReturn(UUID.randomUUID());
    when(row.getActorEmployeeId()).thenReturn(actor);
    when(row.getAction()).thenReturn(action);
    when(row.getOutcome()).thenReturn(outcome);
    when(row.getTargetRef()).thenReturn("sales-order:1");
    when(row.getAt()).thenReturn(Instant.parse("2026-07-22T00:00:00Z"));
    return row;
  }

  private static final ActivityRecord SUCCESS =
      record("emp-sales", BackOfficeAction.TAKE_SALES_ORDER, ActivityOutcome.SUCCESS);
  private static final ActivityRecord DENIED =
      record("emp-whse", BackOfficeAction.TAKE_SALES_ORDER, ActivityOutcome.REJECTED_NOT_PERMITTED);
  private static final ActivityRecord INVALID =
      record("emp-sales", BackOfficeAction.CONFIRM_SALES_PAYMENT, ActivityOutcome.REJECTED_INVALID);
  private static final ActivityRecord UNAVAILABLE =
      record("emp-proc", BackOfficeAction.RAISE_PURCHASE_ORDER, ActivityOutcome.FAILED_UNAVAILABLE);

  @Test
  void filtersByEachOutcome() {
    when(repository.findAllByOrderByAtDesc())
        .thenReturn(List.of(SUCCESS, DENIED, INVALID, UNAVAILABLE));

    for (ActivityOutcome outcome : ActivityOutcome.values()) {
      List<ActivityController.ActivityView> rows =
          controller.list(null, null, outcome, null, null);
      assertThat(rows).hasSize(1);
      assertThat(rows.get(0).outcome()).isEqualTo(outcome);
    }
  }

  @Test
  void outcomeComposesWithTheActionFilter() {
    when(repository.findByActionOrderByAtDesc(BackOfficeAction.TAKE_SALES_ORDER))
        .thenReturn(List.of(SUCCESS, DENIED));

    List<ActivityController.ActivityView> rows =
        controller.list(
            null,
            BackOfficeAction.TAKE_SALES_ORDER,
            ActivityOutcome.REJECTED_NOT_PERMITTED,
            null,
            null);

    assertThat(rows).singleElement().satisfies(r -> assertThat(r.actorEmployeeId()).isEqualTo("emp-whse"));
  }

  @Test
  void withoutAnOutcomeBehavesExactlyAsBefore() {
    when(repository.findAllByOrderByAtDesc())
        .thenReturn(List.of(SUCCESS, DENIED, INVALID, UNAVAILABLE));

    assertThat(controller.list(null, null, null, null, null)).hasSize(4);
  }

  @Test
  void readingRecordsNoActivity() {
    when(repository.findAllByOrderByAtDesc()).thenReturn(List.of(SUCCESS));

    controller.list(null, null, ActivityOutcome.SUCCESS, null, null);

    verify(repository, never()).save(any());
    verify(repository, never()).saveAll(any());
  }
}
