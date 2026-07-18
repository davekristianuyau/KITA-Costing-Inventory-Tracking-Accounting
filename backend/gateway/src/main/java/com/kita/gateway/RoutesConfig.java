package com.kita.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routes each {@code /api/<service>/**} prefix to its private backend service (FR-019). Targets come from
 * environment-scoped properties so the same config works in every environment. Defined in Java (the
 * stable RouteLocator API) to be robust across Spring Cloud Gateway config-namespace changes.
 */
@Configuration
public class RoutesConfig {

  @Bean
  RouteLocator apiRoutes(
      RouteLocatorBuilder builder,
      @Value("${OPERATIONS_SERVICE_URL:http://operations-service:8083}") String operations,
      @Value("${HR_SERVICE_URL:http://hr-service:8085}") String hr,
      @Value("${CRM_SERVICE_URL:http://crm-service:8086}") String crm,
      @Value("${PROCUREMENT_SERVICE_URL:http://procurement-service:8087}") String procurement,
      @Value("${WORKFLOW_SERVICE_URL:http://workflow-service:8088}") String workflow) {
    return builder
        .routes()
        .route("operations-service", r -> r.path("/api/operations/**").uri(operations))
        .route("hr-service", r -> r.path("/api/hr/**").uri(hr))
        .route("crm-service", r -> r.path("/api/crm/**").uri(crm))
        .route("procurement-service", r -> r.path("/api/procurement/**").uri(procurement))
        .route("workflow-service", r -> r.path("/api/workflow/**").uri(workflow))
        .build();
  }
}
