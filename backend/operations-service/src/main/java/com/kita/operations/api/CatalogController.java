package com.kita.operations.api;

import com.kita.operations.api.Dtos.ConversionCreateRequest;
import com.kita.operations.api.Dtos.ItemCreateRequest;
import com.kita.operations.api.Dtos.ItemResponse;
import com.kita.operations.api.Dtos.UomCreateRequest;
import com.kita.operations.api.Dtos.UomResponse;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catalog endpoints: items, units of measure, conversions. */
@RestController
@RequestMapping("/api/operations")
public class CatalogController {

  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse createItem(@Valid @RequestBody ItemCreateRequest req) {
    Item item =
        catalog.createItem(
            req.sku(),
            req.name(),
            req.type(),
            req.baseUom(),
            req.valuationMethod(),
            Boolean.TRUE.equals(req.perishable()));
    return toResponse(item);
  }

  @GetMapping("/items")
  public List<ItemResponse> listItems() {
    // Served from the shared cache (invalidated on item create); falls back to the DB if Redis is down.
    return catalog.listItemViews().stream().map(CatalogController::toResponse).toList();
  }

  @PostMapping("/uoms")
  @ResponseStatus(HttpStatus.CREATED)
  public UomResponse createUom(@Valid @RequestBody UomCreateRequest req) {
    var uom = catalog.createUom(req.code(), req.family());
    return new UomResponse(uom.getId(), uom.getCode(), uom.getFamily());
  }

  @PostMapping("/uom-conversions")
  @ResponseStatus(HttpStatus.CREATED)
  public void createConversion(@Valid @RequestBody ConversionCreateRequest req) {
    catalog.createConversion(req.fromUom(), req.toUom(), req.factor());
  }

  static ItemResponse toResponse(Item item) {
    return new ItemResponse(
        item.getId(),
        item.getSku(),
        item.getName(),
        item.getType(),
        item.getBaseUom().getCode(),
        item.getValuationMethod(),
        item.isPerishable(),
        item.getStandardCost());
  }

  static ItemResponse toResponse(ItemView v) {
    return new ItemResponse(
        v.id(), v.sku(), v.name(), v.type(), v.baseUomCode(), v.valuationMethod(),
        v.perishable(), v.standardCost());
  }
}
