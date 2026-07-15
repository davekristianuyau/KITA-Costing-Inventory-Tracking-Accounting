package com.kita.hr.api;

import com.kita.hr.common.security.CallerContext;
import com.kita.hr.common.security.Role;
import com.kita.hr.remittance.RemittanceService;
import com.kita.hr.remittance.dto.RemittanceResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RemittanceController {

  private final RemittanceService remittances;
  private final CallerContext caller;

  public RemittanceController(RemittanceService remittances, CallerContext caller) {
    this.remittances = remittances;
    this.caller = caller;
  }

  @GetMapping("/api/hr/payroll/runs/{id}/remittances")
  public RemittanceResponse remittances(@PathVariable UUID id) {
    caller.require(Role.HR_ADMIN, Role.PAYROLL_OFFICER);
    return RemittanceResponse.from(remittances.forRun(id));
  }
}
