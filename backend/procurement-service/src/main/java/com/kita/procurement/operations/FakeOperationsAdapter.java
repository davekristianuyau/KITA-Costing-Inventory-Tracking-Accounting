package com.kita.procurement.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link OperationsPort} used until the real HTTP adapter is wired up, and by tests.
 *
 * <p>It genuinely enforces idempotency rather than merely recording calls, so a test that retries a
 * post proves exactly-once behaviour instead of proving the fake is lenient.
 *
 * <p>Selected by {@code procurement.operations.adapter} (default {@code fake}); set it to {@code
 * http} to use the real adapter.
 */
@Component
@ConditionalOnProperty(
    name = "procurement.operations.adapter",
    havingValue = "fake",
    matchIfMissing = true)
public class FakeOperationsAdapter implements OperationsPort {

  private final Set<String> postedKeys = ConcurrentHashMap.newKeySet();
  private final List<GoodsReceiptPost> posted = Collections.synchronizedList(new ArrayList<>());
  private final Map<String, ReorderSignal> signals = new ConcurrentHashMap<>();

  @Override
  public List<ReorderSignal> getReorderSignals() {
    return List.copyOf(signals.values());
  }

  @Override
  public boolean postGoodsReceipt(GoodsReceiptPost receipt) {
    if (!postedKeys.add(receipt.idempotencyKey())) {
      return false; // already posted; a retry must not double-count stock
    }
    posted.add(receipt);
    return true;
  }

  // --- test seams -------------------------------------------------------------------------------

  /** Seed a reorder signal as operations-service would report it. */
  public void seedSignal(ReorderSignal signal) {
    signals.put(signal.itemRef(), signal);
  }

  /** Every receipt actually accepted — a double-post would show up as a duplicate here. */
  public List<GoodsReceiptPost> postedReceipts() {
    return List.copyOf(posted);
  }

  public void reset() {
    postedKeys.clear();
    posted.clear();
    signals.clear();
  }
}
