package com.kita.operations.api;

import com.kita.operations.api.ReceiptDtos.GoodsReceiptRequest;
import com.kita.operations.api.ReceiptDtos.GoodsReceiptResponse;
import com.kita.operations.procurement.GoodsReceipt;
import com.kita.operations.procurement.GoodsReceiptService;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/receipts")
public class GoodsReceiptController {

  private final GoodsReceiptService receipts;

  public GoodsReceiptController(GoodsReceiptService receipts) {
    this.receipts = receipts;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GoodsReceiptResponse post(@Valid @RequestBody GoodsReceiptRequest req) {
    List<ReceiptLineSpec> specs =
        req.lines().stream()
            .map(
                l ->
                    new ReceiptLineSpec(
                        l.itemId(), l.lotCode(), l.expiryDate(), l.quantity(), l.uom(), l.unitCost()))
            .toList();
    GoodsReceipt r = receipts.post(req.supplierRef(), req.locationId(), specs);
    return new GoodsReceiptResponse(r.getId(), r.getSupplierRef(), r.getLocation().getId());
  }
}
