package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, UUID> {
  Optional<Lot> findByItemAndLotCode(Item item, String lotCode);
}
