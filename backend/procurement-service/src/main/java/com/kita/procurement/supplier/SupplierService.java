package com.kita.procurement.supplier;

import com.kita.procurement.common.AuditWriter;
import com.kita.procurement.common.ConflictException;
import com.kita.procurement.common.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Supplier master + supplied items. Changes are retained as history, never overwritten (FR-003). */
@Service
public class SupplierService {

  private final SupplierRepository suppliers;
  private final SupplierItemRepository items;
  private final SupplierChangeHistoryRepository history;
  private final AuditWriter audit;

  public SupplierService(
      SupplierRepository suppliers,
      SupplierItemRepository items,
      SupplierChangeHistoryRepository history,
      AuditWriter audit) {
    this.suppliers = suppliers;
    this.items = items;
    this.history = history;
    this.audit = audit;
  }

  @Transactional
  public Supplier create(CreateSupplierRequest req, String actor) {
    if (suppliers.existsBySupplierCode(req.supplierCode())) {
      throw new ConflictException("supplier_code already exists: " + req.supplierCode());
    }
    Supplier s = new Supplier(req.supplierCode(), req.name());
    s.setEmail(req.email());
    s.setPhone(req.phone());
    s.setAddress(req.address());
    s.setPaymentTerms(req.paymentTerms());
    s.setDeliveryTerms(req.deliveryTerms());
    s.setStatus(SupplierStatus.ACTIVE);
    Supplier saved = suppliers.save(s);
    audit.record(actor, "SUPPLIER_CHANGED", saved.getId().toString(), "created code=" + req.supplierCode());
    return saved;
  }

  @Transactional(readOnly = true)
  public Supplier get(UUID id) {
    return suppliers.findById(id).orElseThrow(() -> new NotFoundException("supplier not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<Supplier> list() {
    return suppliers.findAll();
  }

  @Transactional
  public Supplier update(UUID id, UpdateSupplierRequest req, String actor) {
    Supplier s = get(id);
    List<SupplierChangeHistory> changes = new ArrayList<>();

    track(changes, id, actor, "name", s::getName, s::setName, req.name());
    track(changes, id, actor, "email", s::getEmail, s::setEmail, req.email());
    track(changes, id, actor, "phone", s::getPhone, s::setPhone, req.phone());
    track(changes, id, actor, "address", s::getAddress, s::setAddress, req.address());
    track(changes, id, actor, "paymentTerms", s::getPaymentTerms, s::setPaymentTerms, req.paymentTerms());
    track(changes, id, actor, "deliveryTerms", s::getDeliveryTerms, s::setDeliveryTerms, req.deliveryTerms());
    if (req.status() != null && req.status() != s.getStatus()) {
      changes.add(
          new SupplierChangeHistory(id, null, "status", s.getStatus().name(), req.status().name(), actor));
      s.setStatus(req.status());
    }

    Supplier saved = suppliers.save(s);
    history.saveAll(changes);
    if (!changes.isEmpty()) {
      audit.record(
          actor,
          "SUPPLIER_CHANGED",
          id.toString(),
          "fields=" + changes.stream().map(SupplierChangeHistory::getField).toList());
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public List<SupplierItem> items(UUID supplierId) {
    get(supplierId); // 404 if unknown
    return items.findBySupplierId(supplierId);
  }

  /**
   * Add or update a supplied item. A price change is retained as history — what a PO was raised
   * against must stay reconstructable after the catalog moves on.
   */
  @Transactional
  public SupplierItem upsertItem(UUID supplierId, SupplierItemRequest req, String actor) {
    get(supplierId);
    SupplierItem existing = items.findBySupplierIdAndItemRef(supplierId, req.itemRef()).orElse(null);

    if (existing == null) {
      if (req.preferred()) {
        clearPreferredElsewhere(req.itemRef(), supplierId);
      }
      SupplierItem saved =
          items.save(
              new SupplierItem(
                  supplierId, req.itemRef(), req.supplierPrice(), req.leadTimeDays(),
                  req.minOrderQty(), req.preferred()));
      audit.record(actor, "SUPPLIER_CHANGED", supplierId.toString(), "item added ref=" + req.itemRef());
      return saved;
    }

    List<SupplierChangeHistory> changes = new ArrayList<>();
    if (req.supplierPrice() != null && existing.getSupplierPrice().compareTo(req.supplierPrice()) != 0) {
      changes.add(
          new SupplierChangeHistory(
              supplierId, req.itemRef(), "supplierPrice",
              existing.getSupplierPrice().toPlainString(), req.supplierPrice().toPlainString(), actor));
      existing.setSupplierPrice(req.supplierPrice());
    }
    trackItem(changes, supplierId, req.itemRef(), actor, "leadTimeDays",
        str(existing.getLeadTimeDays()), str(req.leadTimeDays()));
    if (req.leadTimeDays() != null) {
      existing.setLeadTimeDays(req.leadTimeDays());
    }
    trackItem(changes, supplierId, req.itemRef(), actor, "minOrderQty",
        str(existing.getMinOrderQty()), str(req.minOrderQty()));
    if (req.minOrderQty() != null) {
      existing.setMinOrderQty(req.minOrderQty());
    }
    if (req.preferred() != existing.isPreferred()) {
      if (req.preferred()) {
        clearPreferredElsewhere(req.itemRef(), supplierId);
      }
      changes.add(
          new SupplierChangeHistory(
              supplierId, req.itemRef(), "preferred",
              String.valueOf(existing.isPreferred()), String.valueOf(req.preferred()), actor));
      existing.setPreferred(req.preferred());
    }

    SupplierItem saved = items.save(existing);
    history.saveAll(changes);
    if (!changes.isEmpty()) {
      audit.record(
          actor, "SUPPLIER_CHANGED", supplierId.toString(), "item updated ref=" + req.itemRef());
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public List<SupplierChangeHistory> history(UUID supplierId) {
    get(supplierId);
    return history.findBySupplierIdOrderByChangedAtAsc(supplierId);
  }

  /** Only one supplier may be preferred for an item, so restock has an unambiguous default. */
  private void clearPreferredElsewhere(String itemRef, UUID keepSupplierId) {
    items
        .findByItemRefAndPreferredTrue(itemRef)
        .filter(other -> !other.getSupplierId().equals(keepSupplierId))
        .ifPresent(
            other -> {
              other.setPreferred(false);
              items.saveAndFlush(other); // flush before the new preferred row hits the unique index
            });
  }

  private void trackItem(
      List<SupplierChangeHistory> changes,
      UUID supplierId,
      String itemRef,
      String actor,
      String field,
      String oldValue,
      String newValue) {
    if (newValue == null || Objects.equals(oldValue, newValue)) {
      return;
    }
    changes.add(new SupplierChangeHistory(supplierId, itemRef, field, oldValue, newValue, actor));
  }

  // NOTE: java.util.function.Supplier is fully qualified — importing it would shadow the Supplier
  // entity in this package and silently change this class's method signatures.
  private void track(
      List<SupplierChangeHistory> changes,
      UUID id,
      String actor,
      String field,
      java.util.function.Supplier<String> current,
      Consumer<String> setter,
      String next) {
    if (next == null || Objects.equals(next, current.get())) {
      return;
    }
    changes.add(new SupplierChangeHistory(id, null, field, current.get(), next, actor));
    setter.accept(next);
  }

  private static String str(Object v) {
    return v == null ? null : v instanceof BigDecimal b ? b.toPlainString() : String.valueOf(v);
  }
}
