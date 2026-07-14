package com.kita.hr.support;

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
 * Base for hr-service integration tests. Uses a SINGLETON PostgreSQL container started once and
 * shared across all test classes (never stopped by the JUnit lifecycle, which would otherwise tear
 * it down after the first class). Each test starts from a clean slate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractHrIT {

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
    // NOTE: deduction_rule / deduction_rule_row are intentionally NOT truncated — they hold the
    // seeded PH statutory ruleset that payroll computation depends on.
    jdbc.execute(
        "TRUNCATE TABLE pay_component, payslip, payroll_run, pay_period, loan, attendance_record,"
            + " work_schedule, holiday_calendar, premium_rule, compensation_record, employee,"
            + " audit_event RESTART IDENTITY CASCADE");
  }
}
