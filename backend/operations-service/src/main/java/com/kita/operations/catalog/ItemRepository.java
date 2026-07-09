package com.kita.operations.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {
  Optional<Item> findBySku(String sku);

  boolean existsBySku(String sku);
}
