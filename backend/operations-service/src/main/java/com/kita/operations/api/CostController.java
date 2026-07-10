package com.kita.operations.api;

import com.kita.operations.costing.CostingService;
import com.kita.operations.costing.CostingService.CostMargin;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/items")
public class CostController {

  private final CostingService costing;

  public CostController(CostingService costing) {
    this.costing = costing;
  }

  @GetMapping("/{id}/cost")
  public CostMargin cost(
      @PathVariable UUID id, @RequestParam(required = false) BigDecimal salePrice) {
    return costing.margin(id, salePrice);
  }
}
