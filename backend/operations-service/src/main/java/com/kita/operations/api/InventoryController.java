package com.kita.operations.api;

import com.kita.operations.api.Dtos.AdjustmentRequest;
import com.kita.operations.api.Dtos.AvailabilityResponse;
import com.kita.operations.api.Dtos.LocationCreateRequest;
import com.kita.operations.api.Dtos.LocationResponse;
import com.kita.operations.api.Dtos.MovementResponse;
import com.kita.operations.inventory.InventoryService;
import com.kita.operations.inventory.StockLevel;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.inventory.StockMovement;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inventory endpoints: locations, adjustments, availability, movements. */
@RestController
@RequestMapping("/api/operations")
public class InventoryController {

  private final InventoryService inventory;

  public InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @PostMapping("/locations")
  @ResponseStatus(HttpStatus.CREATED)
  public LocationResponse createLocation(@Valid @RequestBody LocationCreateRequest req) {
    StockLocation loc = inventory.createLocation(req.code(), req.name());
    return new LocationResponse(loc.getId(), loc.getCode(), loc.getName());
  }

  @PostMapping("/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  public MovementResponse postAdjustment(@Valid @RequestBody AdjustmentRequest req) {
    StockMovement m =
        inventory.postAdjustment(
            req.itemId(), req.locationId(), req.lotId(), req.quantity(), req.uom(), req.reason());
    return toMovement(m);
  }

  @GetMapping("/items/{id}/availability")
  public List<AvailabilityResponse> availability(@PathVariable UUID id) {
    return inventory.availabilityForItem(id).stream().map(InventoryController::toAvailability).toList();
  }

  @GetMapping("/movements")
  public List<MovementResponse> movements(
      @RequestParam UUID itemId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return inventory.movementsForItem(itemId, from, to).stream()
        .map(InventoryController::toMovement)
        .toList();
  }

  private static AvailabilityResponse toAvailability(StockLevel s) {
    return new AvailabilityResponse(
        s.getItem().getId(),
        s.getLocation().getId(),
        s.getLot() == null ? null : s.getLot().getId(),
        s.getOnHand(),
        s.getReserved(),
        s.getAvailable());
  }

  private static MovementResponse toMovement(StockMovement m) {
    return new MovementResponse(
        m.getId(),
        m.getItem().getId(),
        m.getLocation().getId(),
        m.getLot() == null ? null : m.getLot().getId(),
        m.getType().name(),
        m.getQuantity(),
        m.getUnitCost(),
        m.getReason(),
        m.getSourceType(),
        m.getSourceId(),
        m.getOccurredAt());
  }
}
