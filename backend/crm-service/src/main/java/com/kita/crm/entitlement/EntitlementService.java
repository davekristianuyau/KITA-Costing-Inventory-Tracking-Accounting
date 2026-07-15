package com.kita.crm.entitlement;

import com.kita.crm.common.AuditWriter;
import com.kita.crm.common.NotFoundException;
import com.kita.crm.customer.CustomerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records and reads a customer's government-mandated entitlements (FR-014/016). */
@Service
public class EntitlementService {

  private final EntitlementRepository entitlements;
  private final CustomerRepository customers;
  private final AuditWriter audit;

  public EntitlementService(
      EntitlementRepository entitlements, CustomerRepository customers, AuditWriter audit) {
    this.entitlements = entitlements;
    this.customers = customers;
    this.audit = audit;
  }

  @Transactional
  public Entitlement add(UUID customerId, Entitlement e, String actor) {
    if (!customers.existsById(customerId)) {
      throw new NotFoundException("customer not found: " + customerId);
    }
    Entitlement saved = entitlements.save(e);
    // The supporting ID ref is deliberately absent from the audit detail.
    audit.record(
        actor, "ENTITLEMENT_CHANGED", customerId.toString(), "added kind=" + e.getKind());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Entitlement> forCustomer(UUID customerId) {
    return entitlements.findByCustomerId(customerId);
  }

  /** Entitlements that are valid on {@code date}, regardless of supporting-ID status. */
  @Transactional(readOnly = true)
  public List<Entitlement> validOn(UUID customerId, LocalDate date) {
    return entitlements.findByCustomerId(customerId).stream().filter(e -> e.isValidOn(date)).toList();
  }
}
