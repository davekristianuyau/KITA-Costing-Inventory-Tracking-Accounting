package com.kita.operations.bom;

import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BomService {

  public record ComponentSpec(UUID componentItemId, BigDecimal quantity, String uom) {}

  public record ComponentRequirement(UUID componentItemId, BigDecimal requiredQuantity, String uom) {}

  private final BillOfMaterialsRepository boms;
  private final CatalogService catalog;
  private final UomConversionService uomConversion;

  public BomService(
      BillOfMaterialsRepository boms, CatalogService catalog, UomConversionService uomConversion) {
    this.boms = boms;
    this.catalog = catalog;
    this.uomConversion = uomConversion;
  }

  @Transactional
  public BillOfMaterials create(
      UUID parentItemId, BomType type, BigDecimal outputQuantity, List<ComponentSpec> specs) {
    Item parent = catalog.requireItem(parentItemId);
    if (boms.existsByParentItemAndActiveTrue(parent)) {
      throw new DomainException.Validation("An active BOM already exists for item " + parent.getSku());
    }
    if (specs == null || specs.isEmpty()) {
      throw new DomainException.Validation("A BOM must have at least one component");
    }
    BillOfMaterials bom = new BillOfMaterials(parent, type, outputQuantity);
    for (ComponentSpec s : specs) {
      Item component = catalog.requireItem(s.componentItemId());
      if (component.getId().equals(parent.getId()) || reaches(component, parent.getId())) {
        throw new DomainException.Validation(
            "BOM would create a cycle via component " + component.getSku());
      }
      // Validate the quantity is convertible to the component's base UoM.
      uomConversion.convert(s.quantity(), s.uom(), component.getBaseUom().getCode());
      bom.addComponent(new BomComponent(component, s.quantity(), s.uom()));
    }
    return boms.save(bom);
  }

  @Transactional(readOnly = true)
  public List<ComponentRequirement> explode(UUID parentItemId, BigDecimal quantity) {
    Item parent = catalog.requireItem(parentItemId);
    Map<UUID, BigDecimal> totals = new LinkedHashMap<>();
    Map<UUID, String> uoms = new LinkedHashMap<>();
    explodeInto(parent, quantity, totals, uoms, new HashSet<>());
    List<ComponentRequirement> out = new ArrayList<>();
    totals.forEach((id, qty) -> out.add(new ComponentRequirement(id, qty, uoms.get(id))));
    return out;
  }

  private void explodeInto(
      Item item, BigDecimal qty, Map<UUID, BigDecimal> totals, Map<UUID, String> uoms, Set<UUID> path) {
    var bom = boms.findByParentItemAndActiveTrue(item);
    if (bom.isEmpty()) {
      totals.merge(item.getId(), qty, BigDecimal::add);
      uoms.putIfAbsent(item.getId(), item.getBaseUom().getCode());
      return;
    }
    if (!path.add(item.getId())) {
      throw new DomainException.Validation("Cyclic BOM detected at item " + item.getSku());
    }
    BigDecimal factor = qty.divide(bom.get().getOutputQuantity(), 12, RoundingMode.HALF_UP);
    for (BomComponent c : bom.get().getComponents()) {
      BigDecimal baseQty =
          uomConversion.convert(c.getQuantity(), c.getUom(), c.getComponentItem().getBaseUom().getCode());
      explodeInto(c.getComponentItem(), baseQty.multiply(factor), totals, uoms, path);
    }
    path.remove(item.getId());
  }

  private boolean reaches(Item from, UUID targetId) {
    var bom = boms.findByParentItemAndActiveTrue(from);
    if (bom.isEmpty()) {
      return false;
    }
    for (BomComponent c : bom.get().getComponents()) {
      if (c.getComponentItem().getId().equals(targetId) || reaches(c.getComponentItem(), targetId)) {
        return true;
      }
    }
    return false;
  }
}
