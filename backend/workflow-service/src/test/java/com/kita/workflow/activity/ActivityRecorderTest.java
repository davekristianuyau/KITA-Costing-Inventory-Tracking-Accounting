package com.kita.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kita.workflow.authorization.BackOfficeAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Verifies the recorder scrubs reasons to a single trimmed line (Constitution V, FR-003). */
class ActivityRecorderTest {

  private final ActivityRecordRepository repository = mock(ActivityRecordRepository.class);
  private final ActivityRecorder recorder = new ActivityRecorder(repository);

  @Test
  void collapsesWhitespaceInReason() {
    recorder.record(
        "emp-sales",
        BackOfficeAction.TAKE_SALES_ORDER,
        ActivityOutcome.REJECTED_INVALID,
        "line one\n\t  line two   end",
        "sales-order:1",
        null,
        null,
        0);

    ArgumentCaptor<ActivityRecord> captor = ArgumentCaptor.forClass(ActivityRecord.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getReason()).isEqualTo("line one line two end");
  }

  @Test
  void keepsNullReasonNull() {
    recorder.record(
        "emp-sales",
        BackOfficeAction.TAKE_SALES_ORDER,
        ActivityOutcome.SUCCESS,
        null,
        "sales-order:1",
        null,
        null,
        0);

    ArgumentCaptor<ActivityRecord> captor = ArgumentCaptor.forClass(ActivityRecord.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getReason()).isNull();
  }
}
