package com.kita.workflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import com.kita.workflow.ports.fake.InMemoryOperationsAdapter;
import com.kita.workflow.workflow.BuildWorkflow.BuildRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for production builds (FR-012/013, SC-007) — fakes, no DB. */
class BuildWorkflowTest {

  private final InMemoryOperationsAdapter operations = new InMemoryOperationsAdapter();
  private final BuildWorkflow workflow = new BuildWorkflow(operations);

  @BeforeEach
  void seed() {
    // 1 finished unit consumes 2 comp-a + 3 comp-b
    operations.seedBom(
        "finished", Map.of("comp-a", new BigDecimal("2"), "comp-b", new BigDecimal("3")));
  }

  @Test
  void sufficientComponentsAreConsumedAndFinishedStockRises() {
    operations.seedStock("comp-a", new BigDecimal("10"));
    operations.seedStock("comp-b", new BigDecimal("15"));

    OperationsPort.BuildResult result =
        workflow.build("emp-prod", new BuildRequest("finished", new BigDecimal("3")));

    assertThat(result.produced()).isEqualByComparingTo("3");
    assertThat(operations.availableQty("comp-a")).isEqualByComparingTo("4"); // 10 - 2×3
    assertThat(operations.availableQty("comp-b")).isEqualByComparingTo("6"); // 15 - 3×3
    assertThat(operations.availableQty("finished")).isEqualByComparingTo("3");
  }

  @Test
  void insufficientComponentsRejectWholeBuildWithNothingConsumed() {
    operations.seedStock("comp-a", new BigDecimal("4")); // needs 6 for qty 3
    operations.seedStock("comp-b", new BigDecimal("15"));

    assertThatThrownBy(
            () -> workflow.build("emp-prod", new BuildRequest("finished", new BigDecimal("3"))))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("insufficient components");

    assertThat(operations.availableQty("comp-a")).isEqualByComparingTo("4"); // untouched
    assertThat(operations.availableQty("comp-b")).isEqualByComparingTo("15");
    assertThat(operations.availableQty("finished")).isEqualByComparingTo("0");
  }
}
