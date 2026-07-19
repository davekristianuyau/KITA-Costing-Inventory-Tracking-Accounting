package com.kita.edge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Edge routes: {@code /auth/**} → identity-service (unauthenticated login/logout); {@code /api/**} enters
 * the pipeline against a placeholder URI that {@link SessionAuthFilter} overrides with the caller's
 * tenant backend (resolved from the validated {@code client} claim). The placeholder is never reached.
 */
@Configuration
public class RoutesConfig {

  @Bean
  RouteLocator edgeRoutes(
      RouteLocatorBuilder builder,
      @Value("${IDENTITY_SERVICE_URL:http://identity-service:8090}") String identity) {
    return builder
        .routes()
        .route("identity", r -> r.path("/auth/**").uri(identity))
        .route("client-api", r -> r.path("/api/**").uri("http://edge-unresolved"))
        .build();
  }
}
