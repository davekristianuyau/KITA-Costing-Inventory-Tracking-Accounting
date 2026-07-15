package com.kita.procurement.supplier;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierChangeHistoryRepository
    extends JpaRepository<SupplierChangeHistory, UUID> {
  List<SupplierChangeHistory> findBySupplierIdOrderByChangedAtAsc(UUID supplierId);
}
