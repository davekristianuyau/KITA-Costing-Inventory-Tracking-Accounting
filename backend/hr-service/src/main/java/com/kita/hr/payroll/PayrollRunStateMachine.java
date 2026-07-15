package com.kita.hr.payroll;

import com.kita.hr.common.ConflictException;

/** Guards the payroll-run lifecycle: DRAFT → COMPUTED → FINALIZED (+ CANCELLED from non-final). */
public final class PayrollRunStateMachine {

  private PayrollRunStateMachine() {}

  public static void assertCanCompute(RunStatus status) {
    if (status != RunStatus.DRAFT && status != RunStatus.COMPUTED) {
      throw new ConflictException("run cannot be computed in status " + status);
    }
  }

  public static void assertCanFinalize(RunStatus status) {
    if (status == RunStatus.FINALIZED) {
      throw new ConflictException("run is already finalized");
    }
    if (status != RunStatus.COMPUTED) {
      throw new ConflictException("run must be COMPUTED before finalize (was " + status + ")");
    }
  }

  public static void assertCanCancel(RunStatus status) {
    if (status == RunStatus.FINALIZED) {
      throw new ConflictException("a finalized run cannot be cancelled");
    }
    if (status == RunStatus.CANCELLED) {
      throw new ConflictException("run is already cancelled");
    }
  }
}
