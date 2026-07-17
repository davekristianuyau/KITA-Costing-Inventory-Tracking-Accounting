package com.kita.workflow.workflow;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.OperationsPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Triggers a production build (US5, FR-012/013). A single atomic {@link OperationsPort#build} call —
 * operations explodes the BOM, consumes components and raises finished stock, or rejects the whole
 * build when components are short. No compensation needed (the write is colocated downstream).
 */
@Component
public class BuildWorkflow {

  private final OperationsPort operations;

  public BuildWorkflow(OperationsPort operations) {
    this.operations = operations;
  }

  public record BuildRequest(String itemId, BigDecimal quantity) {}

  public OperationsPort.BuildResult build(String actorEmployeeId, BuildRequest request) {
    if (request.quantity() == null || request.quantity().signum() <= 0) {
      throw new ValidationException("build quantity must be positive");
    }
    return operations.build(request.itemId(), request.quantity());
  }
}
