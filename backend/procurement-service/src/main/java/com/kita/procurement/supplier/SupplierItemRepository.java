package com.kita.procurement.supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: restock needs the preferred supplier and min-order qty for an item. */
public interface SupplierItemRepository extends JpaRepository<SupplierItem, UUID> {
  List<SupplierItem> findBySupplierId(UUID supplierId);

  Optional<SupplierItem> findBySupplierIdAndItemRef(UUID supplierId, String itemRef);

  Optional<SupplierItem> findByItemRefAndPreferredTrue(String itemRef);
}
