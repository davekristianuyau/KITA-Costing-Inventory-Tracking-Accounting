package com.kita.workflow.ports.fake;

import com.kita.workflow.ports.CrmPort;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** In-memory {@link CrmPort} for isolated build/test. Seed active customers with {@link #seed}. */
@Component
@ConditionalOnProperty(name = "workflow.crm.adapter", havingValue = "fake", matchIfMissing = true)
public class InMemoryCrmAdapter implements CrmPort {

  private final Set<String> activeCustomers = ConcurrentHashMap.newKeySet();

  @Override
  public boolean customerActive(String customerId) {
    return activeCustomers.contains(customerId);
  }

  public void seed(String customerId) {
    activeCustomers.add(customerId);
  }

  public void reset() {
    activeCustomers.clear();
  }
}
