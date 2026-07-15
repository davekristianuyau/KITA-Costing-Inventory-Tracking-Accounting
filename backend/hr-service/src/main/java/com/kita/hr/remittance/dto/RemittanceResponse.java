package com.kita.hr.remittance.dto;

import com.kita.hr.remittance.RemittanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RemittanceResponse(
    UUID runId, List<AgencyLineResponse> agencies, BigDecimal grandTotal) {

  public record AgencyLineResponse(
      String agency, BigDecimal employeeTotal, BigDecimal employerTotal, BigDecimal total) {

    static AgencyLineResponse from(RemittanceService.AgencyLine l) {
      return new AgencyLineResponse(l.agency(), l.employeeTotal(), l.employerTotal(), l.total());
    }
  }

  public static RemittanceResponse from(RemittanceService.Summary s) {
    return new RemittanceResponse(
        s.runId(), s.agencies().stream().map(AgencyLineResponse::from).toList(), s.grandTotal());
  }
}
