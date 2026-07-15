package com.kita.crm.entitlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the discount module reads entitlements when building statutory tiers. */
public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {
  List<Entitlement> findByCustomerId(UUID customerId);
}
