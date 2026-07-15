package com.kita.procurement.restock;

import com.kita.procurement.common.AuditWriter;
import com.kita.procurement.common.ConflictException;
import com.kita.procurement.common.NotFoundException;
import com.kita.procurement.operations.OperationsPort;
import com.kita.procurement.purchaseorder.PurchaseOrder;
import com.kita.procurement.purchaseorder.PurchaseOrderOrigin;
import com.kita.procurement.purchaseorder.PurchaseOrderService;
import com.kita.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.kita.procurement.supplier.SupplierItem;
import com.kita.procurement.supplier.SupplierItemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns operations-service's low-stock signals into supplier-grouped restock suggestions, and
 * converts a suggestion into a draft purchase order.
 *
 * <p>Stock levels live in operations-service; this service only reads the signals through the port.
 */
@Service
public class RestockService {

  private final OperationsPort operations;
  private final SupplierItemRepository supplierItems;
  private final RestockSuggestionRepository suggestions;
  private final RestockSuggestionLineRepository suggestionLines;
  private final PurchaseOrderService orders;
  private final AuditWriter audit;

  public RestockService(
      OperationsPort operations,
      SupplierItemRepository supplierItems,
      RestockSuggestionRepository suggestions,
      RestockSuggestionLineRepository suggestionLines,
      PurchaseOrderService orders,
      AuditWriter audit) {
    this.operations = operations;
    this.supplierItems = supplierItems;
    this.suggestions = suggestions;
    this.suggestionLines = suggestionLines;
    this.orders = orders;
    this.audit = audit;
  }

  /** An item that needs replenishing, resolved to its preferred supplier and sized. */
  private record Need(SupplierItem source, String itemRef, BigDecimal qty, BigDecimal onHand, BigDecimal target) {}

  /**
   * Generate suggestions from the current signals, one per supplier covering every item they are the
   * preferred source for (FR-012/013). An item with no preferred supplier is skipped — there is
   * nobody to order it from.
   */
  @Transactional
  public List<RestockSuggestion> generate(String actor) {
    Map<UUID, List<Need>> bySupplier = new LinkedHashMap<>();

    for (OperationsPort.ReorderSignal s : operations.getReorderSignals()) {
      if (!RestockCalculator.needsRestock(s.onHand(), s.reorderPoint())) {
        continue;
      }
      Optional<SupplierItem> preferred = supplierItems.findByItemRefAndPreferredTrue(s.itemRef());
      if (preferred.isEmpty()) {
        continue; // no preferred source; nothing to suggest against
      }
      SupplierItem source = preferred.get();
      BigDecimal qty =
          RestockCalculator.suggestedQty(s.onHand(), s.targetLevel(), source.getMinOrderQty());
      if (qty.signum() <= 0) {
        continue;
      }
      bySupplier
          .computeIfAbsent(source.getSupplierId(), k -> new ArrayList<>())
          .add(new Need(source, s.itemRef(), qty, s.onHand(), s.targetLevel()));
    }

    List<RestockSuggestion> created = new ArrayList<>();
    for (Map.Entry<UUID, List<Need>> e : bySupplier.entrySet()) {
      RestockSuggestion suggestion = suggestions.save(new RestockSuggestion(e.getKey()));
      for (Need n : e.getValue()) {
        suggestionLines.save(
            new RestockSuggestionLine(
                suggestion.getId(),
                n.itemRef(),
                n.qty(),
                n.onHand(),
                n.target(),
                "on hand " + n.onHand() + " at or below reorder point"));
      }
      created.add(suggestion);
      audit.record(
          actor,
          "RESTOCK_SUGGESTED",
          suggestion.getId().toString(),
          "supplier=" + e.getKey() + " lines=" + e.getValue().size());
    }
    return created;
  }

  /**
   * Convert a suggestion into a purchase order. The order is left DRAFT unless every item on it is
   * flagged for auto-submit, which is off by default (FR-014) — replenishment must not quietly spend
   * money.
   */
  @Transactional
  public PurchaseOrder convert(UUID suggestionId, String actor) {
    RestockSuggestion suggestion =
        suggestions
            .findById(suggestionId)
            .orElseThrow(() -> new NotFoundException("restock suggestion not found: " + suggestionId));
    if (suggestion.getStatus() != RestockStatus.OPEN) {
      throw new ConflictException("suggestion is already " + suggestion.getStatus());
    }

    List<RestockSuggestionLine> lines = suggestionLines.findBySuggestionId(suggestionId);
    if (lines.isEmpty()) {
      throw new ConflictException("suggestion has no lines");
    }

    List<CreatePurchaseOrderRequest.LineRequest> poLines = new ArrayList<>();
    boolean everyLineAutoSubmits = true;
    for (RestockSuggestionLine l : lines) {
      SupplierItem item =
          supplierItems
              .findBySupplierIdAndItemRef(suggestion.getSupplierId(), l.getItemRef())
              .orElseThrow(
                  () -> new ConflictException("supplier no longer supplies " + l.getItemRef()));
      everyLineAutoSubmits &= item.isAutoSubmit();
      poLines.add(
          new CreatePurchaseOrderRequest.LineRequest(
              l.getItemRef(), l.getSuggestedQty(), item.getSupplierPrice()));
    }

    PurchaseOrder po =
        orders.create(
            new CreatePurchaseOrderRequest(
                null, suggestion.getSupplierId(), PurchaseOrderOrigin.RESTOCK, poLines),
            actor);
    suggestion.markConverted(po.getId());
    suggestions.save(suggestion);
    audit.record(actor, "RESTOCK_CONVERTED", suggestionId.toString(), "po=" + po.getId());

    if (everyLineAutoSubmits) {
      orders.approve(po.getId(), true, actor);
      return orders.send(po.getId(), actor);
    }
    return po;
  }

  @Transactional
  public RestockSuggestion dismiss(UUID suggestionId, String actor) {
    RestockSuggestion suggestion =
        suggestions
            .findById(suggestionId)
            .orElseThrow(() -> new NotFoundException("restock suggestion not found: " + suggestionId));
    if (suggestion.getStatus() != RestockStatus.OPEN) {
      throw new ConflictException("suggestion is already " + suggestion.getStatus());
    }
    suggestion.setStatus(RestockStatus.DISMISSED);
    RestockSuggestion saved = suggestions.save(suggestion);
    audit.record(actor, "RESTOCK_DISMISSED", suggestionId.toString(), null);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<RestockSuggestion> listOpen() {
    return suggestions.findByStatus(RestockStatus.OPEN);
  }

  @Transactional(readOnly = true)
  public List<RestockSuggestionLine> linesOf(UUID suggestionId) {
    return suggestionLines.findBySuggestionId(suggestionId);
  }
}
