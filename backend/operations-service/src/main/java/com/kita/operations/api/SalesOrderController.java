package com.kita.operations.api;

import com.kita.operations.api.SalesDtos.SalesLineResponse;
import com.kita.operations.api.SalesDtos.SalesOrderCreateRequest;
import com.kita.operations.api.SalesDtos.SalesOrderResponse;
import com.kita.operations.sales.SalesOrder;
import com.kita.operations.sales.SalesOrderLine;
import com.kita.operations.sales.SalesOrderService;
import com.kita.operations.sales.SalesOrderService.LineRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sales-order endpoints: create + lifecycle actions (confirm/fulfill/cancel). */
@RestController
@RequestMapping("/api/operations/sales-orders")
public class SalesOrderController {

  private final SalesOrderService sales;

  public SalesOrderController(SalesOrderService sales) {
    this.sales = sales;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SalesOrderResponse create(@Valid @RequestBody SalesOrderCreateRequest req) {
    List<LineRequest> lines =
        req.lines().stream()
            .map(l -> new LineRequest(l.itemId(), l.quantity(), l.uom(), l.unitPrice()))
            .toList();
    return toResponse(sales.create(req.customerRef(), lines));
  }

  @PostMapping("/{id}/confirm")
  public SalesOrderResponse confirm(@PathVariable UUID id) {
    return toResponse(sales.confirm(id));
  }

  @PostMapping("/{id}/fulfill")
  public SalesOrderResponse fulfill(@PathVariable UUID id) {
    return toResponse(sales.fulfill(id));
  }

  @PostMapping("/{id}/cancel")
  public SalesOrderResponse cancel(@PathVariable UUID id) {
    return toResponse(sales.cancel(id));
  }

  private static SalesOrderResponse toResponse(SalesOrder order) {
    List<SalesLineResponse> lines =
        order.getLines().stream()
            .map(SalesOrderController::toLine)
            .toList();
    return new SalesOrderResponse(
        order.getId(), order.getCustomerRef(), order.getStatus().name(), lines);
  }

  private static SalesLineResponse toLine(SalesOrderLine l) {
    return new SalesLineResponse(
        l.getItem().getId(),
        l.getQuantity(),
        l.getUnitPrice(),
        l.getReservedQty(),
        l.getFulfilledQty());
  }
}
