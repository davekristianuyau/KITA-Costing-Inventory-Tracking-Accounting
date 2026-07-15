package com.kita.crm.api;

import com.kita.crm.common.security.CallerContext;
import com.kita.crm.common.security.Role;
import com.kita.crm.loyalty.LoyaltyService;
import com.kita.crm.loyalty.LoyaltyTier;
import com.kita.crm.loyalty.dto.EvaluateLoyaltyRequest;
import com.kita.crm.loyalty.dto.LoyaltyTierDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoyaltyController {

  private final LoyaltyService loyalty;
  private final CallerContext caller;

  public LoyaltyController(LoyaltyService loyalty, CallerContext caller) {
    this.loyalty = loyalty;
    this.caller = caller;
  }

  @PostMapping("/api/crm/loyalty/tiers")
  @ResponseStatus(HttpStatus.CREATED)
  public LoyaltyTierDto.Response createTier(@Valid @RequestBody LoyaltyTierDto req) {
    caller.require(Role.CRM_ADMIN);
    return LoyaltyTierDto.Response.from(loyalty.createTier(req.toEntity(), caller.actor()));
  }

  @GetMapping("/api/crm/loyalty/tiers")
  public List<LoyaltyTierDto.Response> listTiers() {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    return loyalty.listTiers().stream().map(LoyaltyTierDto.Response::from).toList();
  }

  /** Re-evaluate from supplied activity (FR-011). Returns the assigned tier, or null if none. */
  @PostMapping("/api/crm/customers/{id}/loyalty/evaluate")
  public LoyaltyTierDto.Response evaluate(
      @PathVariable UUID id, @Valid @RequestBody EvaluateLoyaltyRequest req) {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    LoyaltyService.Activity activity =
        new LoyaltyService.Activity(req.purchaseCount(), req.purchaseValue());
    return loyalty.evaluate(id, activity, caller.actor()).map(LoyaltyTierDto.Response::from).orElse(null);
  }
}
