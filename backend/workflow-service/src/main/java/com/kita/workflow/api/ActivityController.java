package com.kita.workflow.api;

import com.kita.workflow.activity.ActivityOutcome;
import com.kita.workflow.activity.ActivityRecord;
import com.kita.workflow.activity.ActivityRecordRepository;
import com.kita.workflow.authorization.BackOfficeAction;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read side of the append-only activity log (FR-003), newest first, with optional filters. */
@RestController
@RequestMapping("/api/workflow/activity")
public class ActivityController {

  private final ActivityRecordRepository repository;

  public ActivityController(ActivityRecordRepository repository) {
    this.repository = repository;
  }

  public record ActivityView(
      String id,
      String actorEmployeeId,
      BackOfficeAction action,
      ActivityOutcome outcome,
      String reason,
      String targetRef,
      String makerEmployeeId,
      int retryCount,
      Instant at) {}

  @GetMapping
  public List<ActivityView> list(
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) BackOfficeAction action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

    List<ActivityRecord> rows;
    if (actor != null && action != null) {
      rows = repository.findByActorEmployeeIdAndActionOrderByAtDesc(actor, action);
    } else if (actor != null) {
      rows = repository.findByActorEmployeeIdOrderByAtDesc(actor);
    } else if (action != null) {
      rows = repository.findByActionOrderByAtDesc(action);
    } else {
      rows = repository.findAllByOrderByAtDesc();
    }
    return rows.stream()
        .filter(r -> from == null || !r.getAt().isBefore(from))
        .filter(r -> to == null || !r.getAt().isAfter(to))
        .map(ActivityController::toView)
        .toList();
  }

  private static ActivityView toView(ActivityRecord r) {
    return new ActivityView(
        r.getId().toString(),
        r.getActorEmployeeId(),
        r.getAction(),
        r.getOutcome(),
        r.getReason(),
        r.getTargetRef(),
        r.getMakerEmployeeId(),
        r.getRetryCount(),
        r.getAt());
  }
}
