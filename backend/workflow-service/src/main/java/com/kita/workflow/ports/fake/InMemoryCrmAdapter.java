package com.kita.workflow.ports.fake;

import com.kita.workflow.ports.CrmPort;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** In-memory {@link CrmPort} for isolated build/test. Seed active customers with {@link #seed}. */
@Component
@ConditionalOnProperty(name = "workflow.crm.adapter", havingValue = "fake", matchIfMissing = true)
public class InMemoryCrmAdapter implements CrmPort {

  private final Set<String> activeCustomers = ConcurrentHashMap.newKeySet();
  private final Set<String> inactiveCustomers = ConcurrentHashMap.newKeySet();

  @Override
  public boolean customerActive(String customerId) {
    return activeCustomers.contains(customerId);
  }

  @Override
  public String createCustomer(CustomerInput input) {
    String id = "cust-" + UUID.randomUUID();
    apply(id, input);
    return id;
  }

  @Override
  public void updateCustomer(String customerId, CustomerInput input) {
    apply(customerId, input);
  }

  private void apply(String id, CustomerInput input) {
    if (input.active()) {
      inactiveCustomers.remove(id);
      activeCustomers.add(id);
    } else {
      activeCustomers.remove(id);
      inactiveCustomers.add(id);
    }
  }

  public void seed(String customerId) {
    activeCustomers.add(customerId);
  }

  public void reset() {
    activeCustomers.clear();
    inactiveCustomers.clear();
  }
}
