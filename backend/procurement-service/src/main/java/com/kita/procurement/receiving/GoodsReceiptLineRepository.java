package com.kita.procurement.receiving;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, UUID> {
  List<GoodsReceiptLine> findByGoodsReceiptId(UUID goodsReceiptId);
}
