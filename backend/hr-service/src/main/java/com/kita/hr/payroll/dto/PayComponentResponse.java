package com.kita.hr.payroll.dto;

import com.kita.hr.payroll.PayComponent;
import com.kita.hr.payroll.PayComponentCategory;
import java.math.BigDecimal;

public record PayComponentResponse(
    PayComponentCategory category, String code, String label, BigDecimal amount, String basis) {

  public static PayComponentResponse from(PayComponent c) {
    return new PayComponentResponse(
        c.getCategory(), c.getCode(), c.getLabel(), c.getAmount(), c.getBasis());
  }
}
