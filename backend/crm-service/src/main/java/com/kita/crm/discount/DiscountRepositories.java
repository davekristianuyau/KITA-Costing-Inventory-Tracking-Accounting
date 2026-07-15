package com.kita.crm.discount;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the discount module (grouped; each is a top-level interface). */
public final class DiscountRepositories {
  private DiscountRepositories() {}
}

interface StackingPolicyRepository extends JpaRepository<StackingPolicy, UUID> {
  List<StackingPolicy> findAllByOrderByUpdatedAtDesc();
}
