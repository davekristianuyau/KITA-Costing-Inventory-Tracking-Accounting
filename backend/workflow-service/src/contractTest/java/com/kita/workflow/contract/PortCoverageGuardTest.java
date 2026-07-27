package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.workflow.ports.CrmPort;
import com.kita.workflow.ports.HrPort;
import com.kita.workflow.ports.OperationsPort;
import com.kita.workflow.ports.ProcurementPort;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * US2 coverage guard (FR-005, scenario 3): every outbound call must be verified against the receiver's
 * real contract. This test reflects over each {@code *Port} and fails if a method has no registered
 * consumer-contract test — so <em>adding</em> an orchestrated call without verifying it is reported
 * rather than silently trusted, which is exactly how the original drift went unnoticed.
 *
 * <p>Adding a port method? Write its contract test, then register it below. The registry is deliberately
 * manual: it is a human statement that the call was checked against the receiver, not an inference.
 */
class PortCoverageGuardTest {

  /** {@code Port#method} → the contract test class that verifies it against the real receiver DTO. */
  private static final Map<String, String> VERIFIED_BY = new LinkedHashMap<>();

  static {
    // operations-service
    VERIFIED_BY.put("OperationsPort#createSalesOrder", "OperationsSalesContractTest");
    VERIFIED_BY.put("OperationsPort#confirmSalesOrder", "LifecycleCallsContractTest");
    VERIFIED_BY.put("OperationsPort#fulfillSalesOrder", "LifecycleCallsContractTest");
    VERIFIED_BY.put("OperationsPort#cancelSalesOrder", "LifecycleCallsContractTest");
    VERIFIED_BY.put("OperationsPort#availability", "LifecycleCallsContractTest");
    VERIFIED_BY.put("OperationsPort#build", "OperationsBuildContractTest");
    // procurement-service
    VERIFIED_BY.put("ProcurementPort#supplierActive", "LifecycleCallsContractTest");
    VERIFIED_BY.put("ProcurementPort#createPurchaseOrder", "ProcurementPoContractTest");
    VERIFIED_BY.put("ProcurementPort#approve", "LifecycleCallsContractTest");
    VERIFIED_BY.put("ProcurementPort#send", "LifecycleCallsContractTest");
    VERIFIED_BY.put("ProcurementPort#receive", "ProcurementReceiptContractTest");
    VERIFIED_BY.put("ProcurementPort#createSupplier", "ProcurementSupplierContractTest");
    VERIFIED_BY.put("ProcurementPort#updateSupplier", "LifecycleCallsContractTest");
    VERIFIED_BY.put("ProcurementPort#setSuppliedItems", "ProcurementSupplierContractTest");
    // crm-service
    VERIFIED_BY.put("CrmPort#customerActive", "LifecycleCallsContractTest");
    VERIFIED_BY.put("CrmPort#createCustomer", "CrmCustomerContractTest");
    VERIFIED_BY.put("CrmPort#updateCustomer", "CrmCustomerContractTest");
    // hr-service
    VERIFIED_BY.put("HrPort#getEmployee", "HrEmployeeContractTest");
  }

  private static final List<Class<?>> PORTS =
      List.of(OperationsPort.class, ProcurementPort.class, CrmPort.class, HrPort.class);

  @Test
  void everyOrchestratedCallHasAContractTest() {
    List<String> unverified = new ArrayList<>();
    for (Class<?> port : PORTS) {
      for (Method method : port.getDeclaredMethods()) {
        if (method.isSynthetic() || method.isDefault() || !method.getDeclaringClass().equals(port)) {
          continue;
        }
        String key = port.getSimpleName() + "#" + method.getName();
        if (!VERIFIED_BY.containsKey(key)) {
          unverified.add(key);
        }
      }
    }
    assertThat(new TreeSet<>(unverified))
        .withFailMessage(
            "unverified orchestrated call(s): %s%n"
                + "Every *Port method must be checked against the RECEIVER's real contract. Add a"
                + " contract test in com.kita.workflow.contract and register it in"
                + " PortCoverageGuardTest.VERIFIED_BY.",
            unverified)
        .isEmpty();
  }

  @Test
  void registryHasNoStaleEntries() {
    List<String> declared = new ArrayList<>();
    for (Class<?> port : PORTS) {
      for (Method method : port.getDeclaredMethods()) {
        if (!method.isSynthetic() && !method.isDefault()) {
          declared.add(port.getSimpleName() + "#" + method.getName());
        }
      }
    }
    List<String> stale = VERIFIED_BY.keySet().stream().filter(k -> !declared.contains(k)).toList();
    assertThat(stale)
        .withFailMessage("registry names call(s) that no longer exist: %s", stale)
        .isEmpty();
  }

  @Test
  @Timeout(30)
  void everyRegisteredContractTestClassActuallyExists() {
    List<String> missing = new ArrayList<>();
    for (String testClass : new TreeSet<>(VERIFIED_BY.values())) {
      try {
        Class.forName(PortCoverageGuardTest.class.getPackageName() + "." + testClass);
      } catch (ClassNotFoundException e) {
        missing.add(testClass);
      }
    }
    assertThat(missing)
        .withFailMessage("registered contract test class(es) not found: %s", missing)
        .isEmpty();
  }
}
