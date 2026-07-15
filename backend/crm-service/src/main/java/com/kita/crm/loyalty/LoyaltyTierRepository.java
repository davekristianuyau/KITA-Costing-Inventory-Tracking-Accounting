package com.kita.crm.loyalty;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the discount module resolves a customer's tier when building the cascade. */
public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, UUID> {
  Optional<LoyaltyTier> findByCode(String code);
}
