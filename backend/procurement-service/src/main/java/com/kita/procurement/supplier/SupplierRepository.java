package com.kita.procurement.supplier;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the purchaseorder and restock modules resolve suppliers. */
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
  boolean existsBySupplierCode(String supplierCode);
}
