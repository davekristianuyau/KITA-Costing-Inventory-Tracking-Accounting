package com.kita.operations.bom;

import com.kita.operations.catalog.Item;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillOfMaterialsRepository extends JpaRepository<BillOfMaterials, UUID> {
  Optional<BillOfMaterials> findByParentItemAndActiveTrue(Item parentItem);

  boolean existsByParentItemAndActiveTrue(Item parentItem);
}
