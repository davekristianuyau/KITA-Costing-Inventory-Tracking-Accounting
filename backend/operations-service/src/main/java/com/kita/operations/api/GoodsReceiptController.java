package com.kita.operations.api;

import com.kita.operations.api.ReceiptDtos.GoodsReceiptLineResponse;
import com.kita.operations.api.ReceiptDtos.GoodsReceiptRequest;
import com.kita.operations.api.ReceiptDtos.GoodsReceiptResponse;
import com.kita.operations.procurement.GoodsReceipt;
import com.kita.operations.procurement.GoodsReceiptService;
import com.kita.operations.procurement.GoodsReceiptService.ReceiptLineSpec;
import com.kita.operations.procurement.ReceiptLine;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping
  public List<GoodsReceiptResponse> list() {
    return receipts.list().stream().map(GoodsReceiptController::toResponse).toList();
  }

  @GetMapping("/{id}")
  public GoodsReceiptResponse getOne(@PathVariable UUID id) {
    return toResponse(receipts.get(id));
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
    return toResponse(receipts.post(req.supplierRef(), req.locationId(), specs));
  }

  private static GoodsReceiptResponse toResponse(GoodsReceipt r) {
    List<GoodsReceiptLineResponse> lines =
        r.getLines().stream().map(GoodsReceiptController::toLine).toList();
    return new GoodsReceiptResponse(
        r.getId(), r.getSupplierRef(), r.getLocation().getId(), r.getReceivedAt(), lines);
  }

  private static GoodsReceiptLineResponse toLine(ReceiptLine l) {
    return new GoodsReceiptLineResponse(
        l.getItem().getId(),
        l.getLot() == null ? null : l.getLot().getId(),
        l.getQuantity(),
        l.getUnitCost());
  }
}
