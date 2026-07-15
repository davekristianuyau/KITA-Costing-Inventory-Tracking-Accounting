package com.kita.crm.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for crm-service integration tests. Uses a SINGLETON PostgreSQL container started once and
 * shared across all test classes (never stopped by the JUnit lifecycle, which would otherwise tear
 * it down after the first class). Each test starts from a clean slate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractCrmIT {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetDatabase() {
    // discount_rule and stacking_policy ARE truncated: most tests configure their own rules, and a
    // leaked rule or a changed policy would silently corrupt every later test. Tests that need the
    // seeded PH ruleset apply it explicitly (see PhStatutorySeedIT).
    jdbc.execute(
        "TRUNCATE TABLE entitlement, customer_attribute_history, customer, loyalty_tier,"
            + " discount_rule, stacking_policy, audit_event RESTART IDENTITY CASCADE");
  }
}
