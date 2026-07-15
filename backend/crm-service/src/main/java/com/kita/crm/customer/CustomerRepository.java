package com.kita.crm.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the discount and loyalty modules resolve customers when computing. */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  boolean existsByCustomerCode(String customerCode);
}
