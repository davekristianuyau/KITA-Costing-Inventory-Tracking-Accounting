package com.kita.operations.catalog;

import com.kita.operations.common.DomainException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Converts quantities between units of measure within the same family. */
@Service
public class UomConversionService {

  private final UnitOfMeasureRepository uoms;
  private final UomConversionRepository conversions;

  public UomConversionService(UnitOfMeasureRepository uoms, UomConversionRepository conversions) {
    this.uoms = uoms;
    this.conversions = conversions;
  }

  /** Convert {@code quantity} expressed in {@code fromCode} into {@code toCode}. */
  public BigDecimal convert(BigDecimal quantity, String fromCode, String toCode) {
    if (fromCode.equals(toCode)) {
      return quantity;
    }
    UnitOfMeasure from =
        uoms.findByCode(fromCode)
            .orElseThrow(() -> new DomainException.Validation("Unknown unit of measure: " + fromCode));
    UnitOfMeasure to =
        uoms.findByCode(toCode)
            .orElseThrow(() -> new DomainException.Validation("Unknown unit of measure: " + toCode));
    if (from.getFamily() != to.getFamily()) {
      throw new DomainException.Validation(
          "Cannot convert across unit families: " + from.getFamily() + " -> " + to.getFamily());
    }
    return conversions
        .findByFromUomAndToUom(from, to)
        .map(c -> quantity.multiply(c.getFactor()))
        .or(
            () ->
                conversions
                    .findByFromUomAndToUom(to, from)
                    .map(c -> quantity.divide(c.getFactor(), 12, java.math.RoundingMode.HALF_UP)))
        .orElseThrow(
            () ->
                new DomainException.Validation(
                    "No conversion defined between " + fromCode + " and " + toCode));
  }
}
