package com.kita.operations.bom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.bom.BomService.ComponentSpec;
import com.kita.operations.catalog.CatalogService;
import com.kita.operations.catalog.Item;
import com.kita.operations.catalog.ItemType;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T033/T034: multi-level explosion quantities and cycle rejection. */
class BomServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired BomService bom;

  private Item item(String sku) {
    return catalog.createItem(sku, sku, ItemType.COMPONENT, "ea", null, false);
  }

  @Test
  void multiLevelExplosionMultipliesQuantities() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item p = item("P");
    Item a = item("A");
    Item b = item("B");
    bom.create(a.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(b.getId(), new BigDecimal("3"), "ea")));
    bom.create(p.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(a.getId(), new BigDecimal("2"), "ea")));

    var reqs = bom.explode(p.getId(), new BigDecimal("5"));

    assertThat(reqs).hasSize(1);
    assertThat(reqs.get(0).componentItemId()).isEqualTo(b.getId());
    assertThat(reqs.get(0).requiredQuantity()).isEqualByComparingTo("30"); // 2 * 3 * 5
  }

  @Test
  void cyclicBomIsRejected() {
    catalog.createUom("ea", UomFamily.COUNT);
    Item x = item("X");
    Item y = item("Y");
    bom.create(x.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
        List.of(new ComponentSpec(y.getId(), BigDecimal.ONE, "ea")));
    assertThatThrownBy(
            () ->
                bom.create(y.getId(), BomType.MANUFACTURED, BigDecimal.ONE,
                    List.of(new ComponentSpec(x.getId(), BigDecimal.ONE, "ea"))))
        .isInstanceOf(DomainException.Validation.class);
  }
}
