package com.kita.workflow.activity;

import com.kita.workflow.authorization.BackOfficeAction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one append-only {@link ActivityRecord} per terminal outcome (FR-003). Runs in its own
 * REQUIRES_NEW transaction so a rejection is still recorded even when the surrounding action rolls
 * back. Reasons are scrubbed of anything but a short message.
 */
@Component
public class ActivityRecorder {

  private final ActivityRecordRepository repository;

  public ActivityRecorder(ActivityRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      String actorEmployeeId,
      BackOfficeAction action,
      ActivityOutcome outcome,
      String reason,
      String targetRef,
      String makerEmployeeId,
      String idempotencyKey,
      int retryCount) {
    repository.save(
        new ActivityRecord(
            actorEmployeeId,
            action,
            outcome,
            scrub(reason),
            targetRef,
            makerEmployeeId,
            idempotencyKey,
            retryCount));
  }

  /** Keep a short, single-line reason; drop anything that looks like a secret token. */
  private String scrub(String reason) {
    if (reason == null) {
      return null;
    }
    String oneLine = reason.replaceAll("\\s+", " ").trim();
    return oneLine.length() > 300 ? oneLine.substring(0, 300) : oneLine;
  }
}
