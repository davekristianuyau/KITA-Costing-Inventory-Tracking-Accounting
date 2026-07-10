package com.kita.operations.support;

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
 * Base for integration tests. Uses a SINGLETON PostgreSQL container started once and shared across
 * all test classes (via the cached Spring context) — it is intentionally never stopped by the
 * JUnit lifecycle, which otherwise tears the container down after the first class and breaks the
 * rest. The JVM/Docker reclaims it at the end of the run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

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

  /** Clean slate before each test so the shared container gives isolated, repeatable state. */
  @BeforeEach
  void resetDatabase() {
    jdbc.execute(
        "TRUNCATE TABLE build, receipt_line, goods_receipt, reservation, sales_order_line,"
            + " sales_order, bom_component, bill_of_materials, stock_movement, stock_level, lot,"
            + " uom_conversion, item, unit_of_measure, stock_location RESTART IDENTITY CASCADE");
  }
}

