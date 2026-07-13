package com.kita.hr.deduction.dto;

import com.kita.hr.deduction.Loan;
import com.kita.hr.deduction.LoanStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record LoanResponse(
    UUID id,
    BigDecimal installmentAmount,
    int installmentsTotal,
    int installmentsPaid,
    BigDecimal outstandingBalance,
    LoanStatus status) {

  public static LoanResponse from(Loan l) {
    return new LoanResponse(
        l.getId(),
        l.getInstallmentAmount(),
        l.getInstallmentsTotal(),
        l.getInstallmentsPaid(),
        l.getOutstandingBalance(),
        l.getStatus());
  }
}
