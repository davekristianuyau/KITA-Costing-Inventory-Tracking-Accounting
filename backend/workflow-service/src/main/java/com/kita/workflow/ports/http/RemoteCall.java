package com.kita.workflow.ports.http;

import com.kita.workflow.common.RetryingCaller;
import com.kita.workflow.common.TransientDownstreamException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.CallerContext;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * Shared downstream-call wrapper for the http adapters (FR-018, SC-010). Every call forwards the acting
 * employee's {@code X-Kita-User} and is retried on a transient failure (timeout/5xx) via
 * {@link RetryingCaller}; every write also carries a stable {@code X-Idempotency-Key} so a replay after
 * a lost response never double-applies. The per-call {@link ResponseHandler} interprets status for its
 * endpoint (a 404 may mean "unknown", not an error); it should raise a
 * {@link TransientDownstreamException} on 5xx so the retry engages.
 */
@Component
public class RemoteCall {

  static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
  static final String USER_HEADER = "X-Kita-User";

  private final RetryingCaller retry;
  private final CallerContext caller;

  public RemoteCall(RetryingCaller retry, CallerContext caller) {
    this.retry = retry;
    this.caller = caller;
  }

  @FunctionalInterface
  public interface ResponseHandler<T> {
    T handle(ConvertibleClientHttpResponse response) throws IOException;
  }

  /** GET (naturally idempotent), retried on transient failure. */
  public <T> T get(RestClient client, String uri, ResponseHandler<T> handler, Object... uriVars) {
    return retry.call(
        "GET " + uri,
        () ->
            client
                .get()
                .uri(uri, uriVars)
                .headers(this::forwardActor)
                .exchange((request, response) -> handler.handle(response)));
  }

  /** A write (POST/PATCH/PUT) with a stable idempotency key, retried on transient failure. */
  public <T> T write(
      RestClient client, HttpMethod method, String uri, Object body, ResponseHandler<T> handler) {
    String idempotencyKey = UUID.randomUUID().toString();
    return retry.call(
        idempotencyKey,
        () -> {
          var spec =
              client
                  .method(method)
                  .uri(uri)
                  .headers(
                      headers -> {
                        forwardActor(headers);
                        headers.add(IDEMPOTENCY_HEADER, idempotencyKey);
                      });
          if (body != null) {
            spec.body(body);
          }
          return spec.exchange((request, response) -> handler.handle(response));
        });
  }

  /** Standard write result: 2xx or (idempotent) 409 → body; 5xx → retry; other 4xx → 422. */
  public <T> T applied(ConvertibleClientHttpResponse response, Class<T> type) throws IOException {
    HttpStatusCode status = response.getStatusCode();
    if (status.is2xxSuccessful() || status.value() == 409) {
      return type == Void.class ? null : response.bodyTo(type);
    }
    if (status.is5xxServerError()) {
      throw new TransientDownstreamException("downstream " + status.value());
    }
    throw new ValidationException("downstream rejected the request: " + status.value());
  }

  private void forwardActor(HttpHeaders headers) {
    String actor = caller.actor();
    if (actor != null && !actor.isBlank()) {
      headers.add(USER_HEADER, actor);
    }
  }
}
