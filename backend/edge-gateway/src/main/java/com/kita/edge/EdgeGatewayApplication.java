package com.kita.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Tenant-aware edge gateway — the single public entry point. Routes {@code /auth/**} to identity and
 * {@code /api/**} to the caller's client backend, selected by the validated {@code client} claim.
 */
@SpringBootApplication
@EnableConfigurationProperties(EdgeProperties.class)
public class EdgeGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(EdgeGatewayApplication.class, args);
  }
}
