package com.kita.edge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Edge routes: {@code /auth/**} → identity-service (unauthenticated login/logout); {@code /api/**} → the
 * caller's client backend gateway. {@link SessionAuthFilter} guards {@code /api/**} globally.
 *
 * <p>US1 wires a single client backend ({@code CLIENT_BACKEND_URL}); US2 (T020) makes {@code /api}
 * routing dynamic, resolving the validated {@code client} claim to {@code <client>-gateway:8081}.
 */
@Configuration
public class RoutesConfig {

  @Bean
  RouteLocator edgeRoutes(
      RouteLocatorBuilder builder,
      @Value("${IDENTITY_SERVICE_URL:http://identity-service:8090}") String identity,
      @Value("${CLIENT_BACKEND_URL:http://client-gateway:8081}") String clientBackend) {
    return builder
        .routes()
        .route("identity", r -> r.path("/auth/**").uri(identity))
        .route("client-api", r -> r.path("/api/**").uri(clientBackend))
        .build();
  }
}
