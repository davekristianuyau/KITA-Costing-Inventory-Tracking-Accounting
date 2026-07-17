package com.kita.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KITA workflow-service: the attributed, authorized back-office layer. It orchestrates the domain
 * services (hr, crm, operations, procurement) and persists only an append-only activity log and a
 * seeded authorization mapping.
 */
@SpringBootApplication
public class WorkflowServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkflowServiceApplication.class, args);
  }
}
