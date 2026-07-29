package com.kita.workflow.pending;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Default {@link PendingReviewStore}: a plain {@link ConcurrentHashMap}. Simplest thing that satisfies
 * SMB scale (Constitution VI); a Redis/queue adapter can replace it via the same port.
 */
@Component
public class InMemoryPendingReviewStore implements PendingReviewStore {

  private final ConcurrentMap<String, PendingReview> items = new ConcurrentHashMap<>();

  @Override
  public String put(PendingReview review) {
    items.put(review.pendingId(), review);
    return review.pendingId();
  }

  @Override
  public Optional<PendingReview> get(String pendingId) {
    return Optional.ofNullable(items.get(pendingId));
  }

  @Override
  public List<PendingReview> list() {
    return List.copyOf(items.values());
  }

  @Override
  public void remove(String pendingId) {
    items.remove(pendingId);
  }
}
