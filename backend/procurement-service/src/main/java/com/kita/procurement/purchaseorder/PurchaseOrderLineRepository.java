package com.kita.procurement.purchaseorder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Public: the receiving module reconciles outstanding quantities against these lines. */
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
  List<PurchaseOrderLine> findByPurchaseOrderId(UUID purchaseOrderId);
}
