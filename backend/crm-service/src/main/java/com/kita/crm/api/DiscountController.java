package com.kita.crm.api;

import com.kita.crm.common.security.CallerContext;
import com.kita.crm.common.security.Role;
import com.kita.crm.discount.DiscountComputationService;
import com.kita.crm.discount.DiscountRuleService;
import com.kita.crm.discount.StackingMode;
import com.kita.crm.discount.dto.ComputeDiscountRequest;
import com.kita.crm.discount.dto.ComputeDiscountResponse;
import com.kita.crm.discount.dto.DiscountRuleDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiscountController {

  private final DiscountComputationService computation;
  private final DiscountRuleService ruleService;
  private final CallerContext caller;

  public DiscountController(
      DiscountComputationService computation, DiscountRuleService ruleService, CallerContext caller) {
    this.computation = computation;
    this.ruleService = ruleService;
    this.caller = caller;
  }

  /** Stateless pricing for the sales flow (FR-015); crm-service does not own order capture. */
  @PostMapping("/api/crm/discounts/compute")
  public ComputeDiscountResponse compute(@Valid @RequestBody ComputeDiscountRequest req) {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    List<DiscountComputationService.LineItem> items =
        req.lineItems().stream()
            .map(
                l ->
                    new DiscountComputationService.LineItem(
                        l.itemRef(), l.quantity(), l.unitPrice()))
            .toList();
    return ComputeDiscountResponse.from(
        computation.compute(req.customerId(), req.saleDate(), items));
  }

  @GetMapping("/api/crm/discount-rules")
  public List<DiscountRuleDto.Response> listRules(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    return ruleService.listEffective(asOf == null ? LocalDate.now() : asOf).stream()
        .map(DiscountRuleDto.Response::from)
        .toList();
  }

  @PostMapping("/api/crm/discount-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public DiscountRuleDto.Response createRule(@Valid @RequestBody DiscountRuleDto req) {
    caller.require(Role.CRM_ADMIN);
    return DiscountRuleDto.Response.from(ruleService.create(req.toEntity(), caller.actor()));
  }

  @GetMapping("/api/crm/discount-policy")
  public PolicyResponse getPolicy() {
    caller.require(Role.CRM_ADMIN, Role.SALES);
    return new PolicyResponse(ruleService.activePolicy().getMode());
  }

  @PutMapping("/api/crm/discount-policy")
  public PolicyResponse setPolicy(@Valid @RequestBody PolicyRequest req) {
    caller.require(Role.CRM_ADMIN);
    return new PolicyResponse(ruleService.setPolicy(req.mode(), caller.actor()).getMode());
  }

  public record PolicyRequest(@NotNull StackingMode mode) {}

  public record PolicyResponse(StackingMode mode) {}
}
