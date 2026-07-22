package com.kita.operations.procurement;

import com.kita.operations.common.AuditWriter;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.common.DomainException;
import com.kita.operations.costing.ValuationService;
import com.kita.operations.inventory.Lot;
import com.kita.operations.inventory.LotRepository;
import com.kita.operations.inventory.MovementType;
import com.kita.operations.inventory.StockLedgerService;
import com.kita.operations.inventory.StockLocation;
import com.kita.operations.inventory.StockLocationRepository;
import com.kita.operations.party.PartyClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Record inbound stock from a supplier: validates the supplier, increases on-hand, updates AVCO. */
@Service
public class GoodsReceiptService {

  public record ReceiptLineSpec(
      UUID itemId,
      String lotCode,
      LocalDate expiryDate,
      BigDecimal quantity,
      String uom,
      BigDecimal unitCost) {}

  private final GoodsReceiptRepository receipts;
  private final StockLocationRepository locations;
  private final LotRepository lots;
  private final CatalogService catalog;
  private final UomConversionService uomConversion;
  private final StockLedgerService ledger;
  private final ValuationService valuation;
  private final PartyClient party;
  private final AuditWriter audit;

  public GoodsReceiptService(
      GoodsReceiptRepository receipts,
      StockLocationRepository locations,
      LotRepository lots,
      CatalogService catalog,
      UomConversionService uomConversion,
      StockLedgerService ledger,
      ValuationService valuation,
      PartyClient party,
      AuditWriter audit) {
    this.audit = audit;
    this.receipts = receipts;
    this.locations = locations;
    this.lots = lots;
    this.catalog = catalog;
    this.uomConversion = uomConversion;
    this.ledger = ledger;
    this.valuation = valuation;
    this.party = party;
  }

  @Transactional(readOnly = true)
  public List<GoodsReceipt> list() {
    List<GoodsReceipt> all = receipts.findAll();
    all.forEach(r -> r.getLines().size()); // initialize lazy lines within the transaction
    return all;
  }

  @Transactional(readOnly = true)
  public GoodsReceipt get(UUID id) {
    GoodsReceipt receipt =
        receipts
            .findById(id)
            .orElseThrow(() -> new DomainException.NotFound("Goods receipt not found: " + id));
    receipt.getLines().size();
    return receipt;
  }

  @Transactional
  public GoodsReceipt post(String supplierRef, UUID locationId, List<ReceiptLineSpec> lineSpecs) {
    if (!party.validateSupplier(supplierRef).isValid()) {
      throw new DomainException.Validation("Unknown or inactive supplier: " + supplierRef);
    }
    StockLocation location =
        locations
            .findById(locationId)
            .orElseThrow(() -> new DomainException.NotFound("Location not found: " + locationId));
    if (lineSpecs == null || lineSpecs.isEmpty()) {
      throw new DomainException.Validation("A goods receipt must have at least one line");
    }
    GoodsReceipt receipt = receipts.save(new GoodsReceipt(supplierRef, location));
    for (ReceiptLineSpec s : lineSpecs) {
      Item item = catalog.requireItem(s.itemId());
      BigDecimal baseQty =
          s.uom() == null
              ? s.quantity()
              : uomConversion.convert(s.quantity(), s.uom(), item.getBaseUom().getCode());
      if (baseQty.signum() <= 0) {
        throw new DomainException.Validation("Receipt quantity must be positive");
      }
      Lot lot = null;
      if (s.lotCode() != null && !s.lotCode().isBlank()) {
        lot =
            lots.findByItemAndLotCode(item, s.lotCode())
                .orElseGet(
                    () -> lots.save(new Lot(item, s.lotCode(), s.expiryDate(), s.unitCost())));
      }
      valuation.applyReceiptCost(item, baseQty, s.unitCost()); // before adding stock
      ledger.apply(
          item, location, lot, MovementType.RECEIPT, baseQty, s.unitCost(),
          "goods receipt", "GOODS_RECEIPT", receipt.getId().toString());
      receipt.addLine(new ReceiptLine(item, lot, baseQty, s.unitCost()));
    }
    GoodsReceipt saved = receipts.save(receipt);
    audit.record(
        null, "GOODS_RECEIPT_POSTED", saved.getId().toString(), "lines=" + lineSpecs.size());
    return saved;
  }
}
