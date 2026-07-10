package com.kita.operations.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.operations.common.DomainException;
import com.kita.operations.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T014: unit-of-measure conversions (kg↔g, tray↔pcs) including cross-family rejection. */
class UomConversionIntegrationTest extends AbstractIntegrationTest {

  @Autowired CatalogService catalog;
  @Autowired UomConversionService conversion;

  @Test
  void convertsWithinFamilyAndInverse() {
    catalog.createUom("kg", UomFamily.MASS);
    catalog.createUom("g", UomFamily.MASS);
    catalog.createConversion("kg", "g", new BigDecimal("1000"));

    assertThat(conversion.convert(new BigDecimal("1"), "kg", "g")).isEqualByComparingTo("1000");
    assertThat(conversion.convert(new BigDecimal("500"), "g", "kg")).isEqualByComparingTo("0.5");
  }

  @Test
  void rejectsCrossFamilyConversion() {
    catalog.createUom("tray", UomFamily.COUNT);
    catalog.createUom("m", UomFamily.LENGTH);
    assertThatThrownBy(() -> conversion.convert(new BigDecimal("1"), "tray", "m"))
        .isInstanceOf(DomainException.Validation.class);
  }
}
