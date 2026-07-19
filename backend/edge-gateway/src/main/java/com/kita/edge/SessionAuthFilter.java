package com.kita.edge;

import com.kita.session.InvalidSessionException;
import com.kita.session.SessionToken;
import com.kita.session.SessionVerifier;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Guards and routes {@code /api/**} (contracts/edge-routing.md):
 *
 * <ol>
 *   <li>read the httpOnly session cookie → decrypt + asymmetric-verify + expiry, else 401;
 *   <li>resolve the validated {@code client} claim to that client's backend (unknown/inactive → 401);
 *   <li>strip any inbound {@code X-Kita-*} (anti-spoofing) and set trusted {@code X-Kita-User/Client/Roles};
 *   <li>route <b>only</b> to the resolved backend (no fallback); unreachable backend → 503.
 * </ol>
 *
 * <p>Runs after {@code RouteToRequestUrlFilter} (order 10000) so it can override the target URL per tenant.
 * {@code /auth/**} and health pass through untouched.
 */
@Component
public class SessionAuthFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(SessionAuthFilter.class);
  private static final String KITA_PREFIX = "X-Kita-";

  private final SessionVerifier verifier;
  private final EdgeProperties props;

  public SessionAuthFilter(SessionVerifier verifier, EdgeProperties props) {
    this.verifier = verifier;
    this.props = props;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    if (!request.getURI().getPath().startsWith("/api/")) {
      return chain.filter(exchange);
    }

    HttpCookie cookie = request.getCookies().getFirst(props.getCookie().getName());
    if (cookie == null || cookie.getValue().isBlank()) {
      return unauthorized(exchange, "no session cookie");
    }

    SessionToken session;
    try {
      session = verifier.verify(cookie.getValue());
    } catch (InvalidSessionException e) {
      return unauthorized(exchange, e.getMessage());
    }

    String backend = props.getBackends().get(session.client());
    if (backend == null || backend.isBlank()) {
      return unauthorized(exchange, "unknown or inactive client: " + session.client());
    }

    // Override the routing target with the tenant's backend, preserving path + query.
    URI base = URI.create(backend);
    URI target =
        UriComponentsBuilder.fromUri(request.getURI())
            .scheme(base.getScheme())
            .host(base.getHost())
            .port(base.getPort())
            .build(true)
            .toUri();
    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, target);
    log.info(
        "edge route client={} user={} path={}",
        session.client(),
        session.subject(),
        request.getURI().getPath());

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

    return chain
        .filter(exchange.mutate().request(mutated).build())
        .onErrorResume(
            ex -> isConnectFailure(ex) && !exchange.getResponse().isCommitted(),
            ex -> {
              log.info("edge backend unreachable client={} target={}", session.client(), base);
              exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
              return exchange.getResponse().setComplete();
            });
  }

  private static boolean isConnectFailure(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof IOException) {
        return true;
      }
    }
    return false;
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
    log.info("edge auth reject path={} reason={}", exchange.getRequest().getURI().getPath(), reason);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Cookie");
    return exchange.getResponse().setComplete();
  }

  // After RouteToRequestUrlFilter (10000), before NettyRoutingFilter (LOWEST), so the per-tenant
  // target override and header mutation both reach the proxied request.
  @Override
  public int getOrder() {
    return 10100;
  }
}
