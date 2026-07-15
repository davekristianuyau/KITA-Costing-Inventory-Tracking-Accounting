package com.kita.crm.customer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the customer module (grouped; each is a top-level interface). */
public final class CustomerRepositories {
  private CustomerRepositories() {}
}

/** Public: the discount and loyalty modules resolve customers when computing. */
interface CustomerAttributeHistoryRepository
    extends JpaRepository<CustomerAttributeHistory, UUID> {
  List<CustomerAttributeHistory> findByCustomerIdOrderByChangedAtAsc(UUID customerId);
}
