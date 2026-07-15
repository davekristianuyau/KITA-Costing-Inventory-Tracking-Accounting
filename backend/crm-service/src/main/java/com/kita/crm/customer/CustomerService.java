package com.kita.crm.customer;

import com.kita.crm.common.AuditWriter;
import com.kita.crm.common.ConflictException;
import com.kita.crm.common.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer master. Every attribute change is retained as history rather than overwritten (FR-003). */
@Service
public class CustomerService {

  private final CustomerRepository customers;
  private final CustomerAttributeHistoryRepository history;
  private final AuditWriter audit;

  public CustomerService(
      CustomerRepository customers, CustomerAttributeHistoryRepository history, AuditWriter audit) {
    this.customers = customers;
    this.history = history;
    this.audit = audit;
  }

  @Transactional
  public Customer create(CreateCustomerRequest req, String actor) {
    if (customers.existsByCustomerCode(req.customerCode())) {
      throw new ConflictException("customer_code already exists: " + req.customerCode());
    }
    Customer c = new Customer(req.customerCode(), req.type(), req.name());
    c.setEmail(req.email());
    c.setPhone(req.phone());
    c.setAddress(req.address());
    c.setStatus(CustomerStatus.ACTIVE);
    Customer saved = customers.save(c);
    audit.record(
        actor, "CUSTOMER_CHANGED", saved.getId().toString(), "created code=" + req.customerCode());
    return saved;
  }

  @Transactional(readOnly = true)
  public Customer get(UUID id) {
    return customers.findById(id).orElseThrow(() -> new NotFoundException("customer not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Customer> list() {
    return customers.findAll();
  }

  @Transactional
  public Customer update(UUID id, UpdateCustomerRequest req, String actor) {
    Customer c = get(id);
    List<CustomerAttributeHistory> changes = new ArrayList<>();

    track(changes, id, actor, "name", c::getName, c::setName, req.name());
    track(changes, id, actor, "email", c::getEmail, c::setEmail, req.email());
    track(changes, id, actor, "phone", c::getPhone, c::setPhone, req.phone());
    track(changes, id, actor, "address", c::getAddress, c::setAddress, req.address());
    if (req.status() != null && req.status() != c.getStatus()) {
      changes.add(
          new CustomerAttributeHistory(
              id, "status", c.getStatus().name(), req.status().name(), actor));
      c.setStatus(req.status());
    }

    Customer saved = customers.save(c);
    history.saveAll(changes);
    if (!changes.isEmpty()) {
      audit.record(
          actor,
          "CUSTOMER_CHANGED",
          id.toString(),
          "fields=" + changes.stream().map(CustomerAttributeHistory::getField).toList());
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public List<CustomerAttributeHistory> history(UUID id) {
    get(id); // 404 if missing
    return history.findByCustomerIdOrderByChangedAtAsc(id);
  }

  /** Apply a field change only when it actually differs, recording the prior value. */
  private void track(
      List<CustomerAttributeHistory> changes,
      UUID id,
      String actor,
      String field,
      Supplier<String> current,
      Consumer<String> setter,
      String next) {
    if (next == null || Objects.equals(next, current.get())) {
      return;
    }
    changes.add(new CustomerAttributeHistory(id, field, current.get(), next, actor));
    setter.accept(next);
  }
}
