package com.kita.procurement.operations;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The real {@link OperationsPort}: an HTTP client to operations-service.
 *
 * <p>Selected by {@code procurement.operations.adapter=http}; the in-memory fake is the default so
 * this service can be built and tested in isolation.
 *
 * <p><b>Retry safety.</b> Every post carries the receipt's idempotency key in a header, so
 * operations-service can recognise a replay. A 409 is therefore treated as success-already-applied
 * rather than an error: the receipt landed, we simply were not the call that applied it. That is
 * what makes a retry after a timeout safe (FR-011, SC-004).
 */
@Component
@ConditionalOnProperty(name = "procurement.operations.adapter", havingValue = "http")
public class HttpOperationsAdapter implements OperationsPort {

  private static final Logger log = LoggerFactory.getLogger(HttpOperationsAdapter.class);

  /** operations-service reads this to collapse replays of the same receipt. */
  static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";

  private final RestClient client;

  public HttpOperationsAdapter(
      RestClient.Builder builder,
      @Value("${procurement.operations.base-url:http://operations-service:8081}") String baseUrl) {
    this.client = builder.baseUrl(baseUrl).build();
  }

  @Override
  public List<ReorderSignal> getReorderSignals() {
    ReorderSignal[] signals =
        client
            .get()
            .uri("/api/operations/inventory/reorder-signals")
            .retrieve()
            .body(ReorderSignal[].class);
    return signals == null ? List.of() : List.of(signals);
  }

  @Override
  public boolean postGoodsReceipt(GoodsReceiptPost receipt) {
    return Boolean.TRUE.equals(
        client
            .post()
            .uri("/api/operations/goods-receipts")
            .header(IDEMPOTENCY_HEADER, receipt.idempotencyKey())
            .body(receipt)
            .exchange(
                (request, response) -> {
                  HttpStatusCode status = response.getStatusCode();
                  if (status.is2xxSuccessful()) {
                    return true;
                  }
                  if (status.value() == 409) {
                    // Already applied under this key — a replay, not a failure. Do not re-post.
                    log.info(
                        "goods receipt {} was already posted to operations-service", receipt.receiptId());
                    return false;
                  }
                  throw new OperationsPostException(
                      "operations-service rejected goods receipt "
                          + receipt.receiptId()
                          + ": HTTP "
                          + status.value());
                }));
  }

  /** Raised when operations-service could not accept a receipt; the caller may retry safely. */
  public static class OperationsPostException extends RuntimeException {
    public OperationsPostException(String message) {
      super(message);
    }
  }
}
