package com.kita.edge;

import com.kita.edge.token.SessionTokenVerifier;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Guards {@code /api/**}: reads the httpOnly session cookie, verifies it (decrypt + asymmetric verify +
 * expiry), then **strips any inbound {@code X-Kita-*}** (anti-spoofing) and sets trusted
 * {@code X-Kita-User}/{@code X-Kita-Client}/{@code X-Kita-Roles} from the token before proxying. Missing
 * or invalid token → 401 (contracts/edge-routing.md). {@code /auth/**} and health pass through untouched.
 */
@Component
public class SessionAuthFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);
  private static final String KITA_PREFIX = "X-Kita-";

  private final SessionTokenVerifier verifier;
  private final String cookieName;

  public SessionAuthFilter(
      SessionTokenVerifier verifier, @Value("${edge.cookie.name:kita_session}") String cookieName) {
    this.verifier = verifier;
    this.cookieName = cookieName;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (!request.getURI().getPath().startsWith("/api/")) {
      return chain.filter(exchange);
    }

    HttpCookie cookie = request.getCookies().getFirst(cookieName);
    if (cookie == null || cookie.getValue().isBlank()) {
      return unauthorized(exchange, "no session cookie");
    }

    SessionTokenVerifier.Session session;
    try {
      session = verifier.verify(cookie.getValue());
    } catch (SessionTokenVerifier.InvalidSessionException e) {
      return unauthorized(exchange, e.getMessage());
    }

    ServerHttpRequest mutated =
        request
            .mutate()
            .headers(
                headers -> {
                  List<String> spoofed =
                      headers.keySet().stream()
                          .filter(n -> n.regionMatches(true, 0, KITA_PREFIX, 0, KITA_PREFIX.length()))
                          .toList();
                  spoofed.forEach(headers::remove);
                  headers.set("X-Kita-User", session.subject());
                  headers.set("X-Kita-Client", session.client());
                  headers.set("X-Kita-Roles", String.join(",", session.roles()));
                })
            .build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
    log.info(
        "edge auth reject path={} reason={}", exchange.getRequest().getURI().getPath(), reason);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Cookie");
    return exchange.getResponse().setComplete();
  }

  // Run before the routing filters so header mutation reaches the proxied request.
  @Override
  public int getOrder() {
    return -1;
  }
}
