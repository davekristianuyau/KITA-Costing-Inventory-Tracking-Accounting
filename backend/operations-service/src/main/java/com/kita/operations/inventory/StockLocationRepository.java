package com.kita.operations.inventory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLocationRepository extends JpaRepository<StockLocation, UUID> {
  Optional<StockLocation> findByCode(String code);
}
