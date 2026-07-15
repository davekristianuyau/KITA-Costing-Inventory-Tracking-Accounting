package com.kita.hr.deduction.dto;

import com.kita.hr.deduction.Computation;
import com.kita.hr.deduction.DeductionBase;
import com.kita.hr.deduction.DeductionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateDeductionRuleRequest(
    @NotBlank String code,
    @NotNull DeductionKind kind,
    @NotNull Computation computation,
    @NotNull DeductionBase base,
    String agency,
    BigDecimal rate,
    BigDecimal employerRate,
    BigDecimal fixedAmount,
    BigDecimal floor,
    BigDecimal cap,
    @NotNull LocalDate effectiveDate,
    List<RowRequest> rows) {

  public record RowRequest(
      BigDecimal low,
      BigDecimal high,
      BigDecimal employeeAmount,
      BigDecimal employerAmount,
      BigDecimal baseTax,
      BigDecimal rateOnExcess,
      BigDecimal excessOver) {}
}
