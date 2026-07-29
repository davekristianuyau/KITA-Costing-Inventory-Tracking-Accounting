package com.kita.workflow.pending;

import java.util.List;
import java.util.Optional;

/**
 * Port for the transient in-flight review state (Clarify Q5). The default is in-memory; a
 * cache/queue adapter can replace it later with no caller change. No durability by design — loss ⇒
 * the maker re-records, no domain effect.
 */
public interface PendingReviewStore {

  /** Store a pending review-gated action / sales position; returns its handle. */
  String put(PendingReview review);

  Optional<PendingReview> get(String pendingId);

  /** Read-only snapshot of everything currently awaiting a checker (FR-005). Never mutates. */
  List<PendingReview> list();

  /** Clear after the durable downstream write (or on completion). */
  void remove(String pendingId);
}
