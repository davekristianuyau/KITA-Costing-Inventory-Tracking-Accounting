package com.kita.operations.api;

import com.kita.operations.api.BomDtos.BomCreateRequest;
import com.kita.operations.api.BomDtos.BomResponse;
import com.kita.operations.api.BomDtos.ComponentRequirementResponse;
import com.kita.operations.bom.BillOfMaterials;
import com.kita.operations.bom.BomService;
import com.kita.operations.bom.BomService.ComponentSpec;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/boms")
public class BomController {

  private final BomService bom;

  public BomController(BomService bom) {
    this.bom = bom;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BomResponse create(@Valid @RequestBody BomCreateRequest req) {
    List<ComponentSpec> specs =
        req.components().stream()
            .map(c -> new ComponentSpec(c.componentItemId(), c.quantity(), c.uom()))
            .toList();
    BillOfMaterials created = bom.create(req.parentItemId(), req.type(), req.outputQuantity(), specs);
    return new BomResponse(
        created.getId(), created.getParentItem().getId(), created.getType(),
        created.getOutputQuantity());
  }

  @GetMapping("/{parentItemId}/explosion")
  public List<ComponentRequirementResponse> explode(
      @PathVariable UUID parentItemId,
      @RequestParam(defaultValue = "1") BigDecimal quantity) {
    return bom.explode(parentItemId, quantity).stream()
        .map(r -> new ComponentRequirementResponse(r.componentItemId(), r.requiredQuantity(), r.uom()))
        .toList();
  }
}
