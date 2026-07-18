package com.kita.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Tenant-aware edge gateway — the single public entry point. Routes {@code /auth/**} to identity and
 * {@code /api/**} to the caller's client backend, selected by the validated {@code client} claim.
 */
@SpringBootApplication
public class EdgeGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(EdgeGatewayApplication.class, args);
  }
}
