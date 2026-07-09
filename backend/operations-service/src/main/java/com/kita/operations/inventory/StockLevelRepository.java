package com.kita.operations.inventory;

import com.kita.operations.catalog.Item;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

  List<StockLevel> findByItem(Item item);

  /** Pessimistic lock for reservation/consumption (no-oversell). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from StockLevel s where s.item = :item and s.location = :loc"
          + " and (:lotId is null or s.lot.id = :lotId)")
  List<StockLevel> lockByItemLocationLot(
      @Param("item") Item item, @Param("loc") StockLocation location, @Param("lotId") UUID lotId);
}
