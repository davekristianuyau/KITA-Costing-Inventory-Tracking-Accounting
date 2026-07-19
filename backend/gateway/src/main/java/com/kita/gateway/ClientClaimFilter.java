package com.kita.gateway;

import com.kita.session.InvalidSessionException;
import com.kita.session.SessionToken;
import com.kita.session.SessionVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Defense-in-depth (FR-006): even though the edge already routed by the {@code client} claim, this
 * per-client gateway independently verifies the session token (asymmetric — only identity's key validates)
 * and rejects any token whose {@code client} ≠ this gateway's configured {@code CLIENT_ID}. So a mis-routed,
 * replayed, or forged token is refused here too. Enforcement is active only when {@code CLIENT_ID} is set;
 * unset (feature-008 standalone) → pass-through.
 */
@Component
public class ClientClaimFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(ClientClaimFilter.class);

  private final SessionVerifier verifier;
  private final String clientId;
  private final String cookieName;

  public ClientClaimFilter(
      SessionVerifier verifier,
      @Value("${gateway.client-id:}") String clientId,
      @Value("${gateway.cookie.name:kita_session}") String cookieName) {
    this.verifier = verifier;
    this.clientId = clientId;
    this.cookieName = cookieName;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (clientId.isBlank()) {
      return chain.filter(exchange); // enforcement disabled (standalone / no tenant configured)
    }
    ServerHttpRequest request = exchange.getRequest();
    if (!request.getURI().getPath().startsWith("/api/")) {
      return chain.filter(exchange);
    }

    HttpCookie cookie = request.getCookies().getFirst(cookieName);
    if (cookie == null || cookie.getValue().isBlank()) {
      return reject(exchange, HttpStatus.UNAUTHORIZED, "no session cookie");
    }

    SessionToken token;
    try {
      token = verifier.verify(cookie.getValue());
    } catch (InvalidSessionException e) {
      return reject(exchange, HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    if (!clientId.equals(token.client())) {
      return reject(
          exchange, HttpStatus.FORBIDDEN, "client " + token.client() + " != " + clientId);
    }
    return chain.filter(exchange);
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String reason) {
    log.info(
        "gateway[{}] reject path={} status={} reason={}",
        clientId,
        exchange.getRequest().getURI().getPath(),
        status.value(),
        reason);
    exchange.getResponse().setStatusCode(status);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return -1; // before routing to the private services
  }
}
