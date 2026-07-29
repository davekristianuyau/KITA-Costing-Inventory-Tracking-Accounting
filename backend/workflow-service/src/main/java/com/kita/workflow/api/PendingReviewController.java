package com.kita.workflow.api;

import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.pending.PendingReview;
import com.kita.workflow.pending.PendingReviewStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the transient maker-checker queue (FR-005), oldest first. A projection of the
 * {@link PendingReviewStore} — it confirms nothing and records no activity (FR-012). The queue is
 * in-memory by design: on restart it empties and the maker re-records, with no domain effect.
 */
@RestController
@RequestMapping("/api/workflow/pending-reviews")
public class PendingReviewController {

  private final PendingReviewStore store;

  public PendingReviewController(PendingReviewStore store) {
    this.store = store;
  }

  /**
   * Identity and position only. The stored {@code payload} — the captured request replayed on confirm
   * — is deliberately absent: it is internal, may not be serialisable, and the UI has no use for it.
   */
  public record PendingReviewView(
      String pendingId,
      BackOfficeAction action,
      String makerEmployeeId,
      String targetRef,
      String stage,
      Instant createdAt) {}

  @GetMapping
  public List<PendingReviewView> list(@RequestParam(required = false) BackOfficeAction action) {
    return store.list().stream()
        .filter(r -> action == null || r.action() == action)
        .sorted(Comparator.comparing(PendingReview::createdAt))
        .map(
            r ->
                new PendingReviewView(
                    r.pendingId(),
                    r.action(),
                    r.makerEmployeeId(),
                    r.targetRef(),
                    r.stage(),
                    r.createdAt()))
        .toList();
  }
}
