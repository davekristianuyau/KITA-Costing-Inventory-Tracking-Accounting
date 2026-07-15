package com.kita.procurement.purchaseorder;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

  boolean existsByPoNo(String poNo);

  /**
   * Row-locking read used by every state transition, so two concurrent approvals (or receipts)
   * serialise and exactly one wins rather than both reading DRAFT and both proceeding.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PurchaseOrder p where p.id = :id")
  Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);
}
