package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  List<StockMovement> findByItemOrderByOccurredAtAsc(Item item);

  List<StockMovement> findByItemAndOccurredAtBetweenOrderByOccurredAtAsc(
      Item item, Instant from, Instant to);
}
