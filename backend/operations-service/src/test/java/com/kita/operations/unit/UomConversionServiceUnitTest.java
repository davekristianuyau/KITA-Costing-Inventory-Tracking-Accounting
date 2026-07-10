package com.kita.operations.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kita.operations.catalog.UnitOfMeasureRepository;
import com.kita.operations.catalog.UomConversionRepository;
import com.kita.operations.catalog.UnitOfMeasure;
import com.kita.operations.catalog.UomConversion;
import com.kita.operations.catalog.UomConversionService;
import com.kita.operations.catalog.UomFamily;
import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Docker-free unit test of UoM conversion (T014 logic). */
@ExtendWith(MockitoExtension.class)
class UomConversionServiceUnitTest {

  @Mock UnitOfMeasureRepository uoms;
  @Mock UomConversionRepository conversions;

  @Test
  void convertsKilogramsToGramsAndInverse() {
    UomConversionService svc = new UomConversionService(uoms, conversions);
    UnitOfMeasure kg = new UnitOfMeasure("kg", UomFamily.MASS);
    UnitOfMeasure g = new UnitOfMeasure("g", UomFamily.MASS);
    when(uoms.findByCode("kg")).thenReturn(Optional.of(kg));
    when(uoms.findByCode("g")).thenReturn(Optional.of(g));
    when(conversions.findByFromUomAndToUom(kg, g))
        .thenReturn(Optional.of(new UomConversion(kg, g, new BigDecimal("1000"))));

    assertThat(svc.convert(new BigDecimal("1"), "kg", "g")).isEqualByComparingTo("1000");

    when(conversions.findByFromUomAndToUom(g, kg)).thenReturn(Optional.empty());
    assertThat(svc.convert(new BigDecimal("500"), "g", "kg")).isEqualByComparingTo("0.5");
  }

  @Test
  void rejectsCrossFamilyConversion() {
    UomConversionService svc = new UomConversionService(uoms, conversions);
    when(uoms.findByCode("tray")).thenReturn(Optional.of(new UnitOfMeasure("tray", UomFamily.COUNT)));
    when(uoms.findByCode("m")).thenReturn(Optional.of(new UnitOfMeasure("m", UomFamily.LENGTH)));
    assertThatThrownBy(() -> svc.convert(new BigDecimal("1"), "tray", "m"))
        .isInstanceOf(DomainException.Validation.class);
  }
}
