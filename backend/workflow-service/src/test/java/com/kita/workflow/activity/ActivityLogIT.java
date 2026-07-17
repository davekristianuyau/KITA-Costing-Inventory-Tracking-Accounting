package com.kita.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.authorization.AuthorizationMappingRepository;
import com.kita.workflow.authorization.BackOfficeAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers IT (CI only — needs Docker): the append-only activity log persists and reads back,
 * and the seeded authorization_mapping loads. Runs on Linux/CI; skipped locally when Docker is off.
 */
@SpringBootTest
@Testcontainers
class ActivityLogIT {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired ActivityRecordRepository activityRepository;
  @Autowired AuthorizationMappingRepository authorizationRepository;
  @Autowired ActivityRecorder recorder;

  @Test
  void authorizationMappingSeedLoads() {
    assertThat(authorizationRepository.count()).isEqualTo(16);
  }

  @Test
  void appendsAndReadsActivityNewestFirst() {
    recorder.record(
        "emp-sales",
        BackOfficeAction.TAKE_SALES_ORDER,
        ActivityOutcome.SUCCESS,
        null,
        "sales-order:it-1",
        null,
        null,
        0);
    var rows = activityRepository.findByActorEmployeeIdOrderByAtDesc("emp-sales");
    assertThat(rows).isNotEmpty();
    assertThat(rows.get(0).getTargetRef()).isEqualTo("sales-order:it-1");
    assertThat(rows.get(0).getOutcome()).isEqualTo(ActivityOutcome.SUCCESS);
  }
}
