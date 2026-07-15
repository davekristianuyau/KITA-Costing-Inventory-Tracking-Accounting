package com.kita.procurement.support;

import com.kita.procurement.operations.FakeOperationsAdapter;
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
 * Base for procurement-service integration tests. Uses a SINGLETON PostgreSQL container started once
 * and shared across all test classes (never stopped by the JUnit lifecycle, which would otherwise
 * tear it down after the first class). Each test starts from a clean slate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractProcurementIT {

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

  /** The fake is a singleton bean, so its state must be reset or receipts leak between tests. */
  @Autowired protected FakeOperationsAdapter operations;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetState() {
    jdbc.execute("TRUNCATE TABLE audit_event RESTART IDENTITY CASCADE");
    operations.reset();
  }
}
