package com.kita.crm.discount;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kita.crm.support.AbstractCrmIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T017: contract tests for /discounts/compute, /discount-rules, /discount-policy. */
class DiscountApiContractTest extends AbstractCrmIT {

  private void rule(String code, String origin, String value, int priority) throws Exception {
    mockMvc
        .perform(
            post("/api/crm/discount-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"%s","origin":"%s","computation":"PERCENT","value":%s,
                     "priority":%d,"effectiveDate":"2026-01-01"}
                    """
                        .formatted(code, origin, value, priority)))
        .andExpect(status().isCreated());
  }

  private String compute(String customerId) {
    String cust = customerId == null ? "null" : "\"" + customerId + "\"";
    return """
        {"customerId":%s,"saleDate":"2026-03-01",
         "lineItems":[{"itemRef":"SKU-1","quantity":"1","unitPrice":"1000.00"}]}
        """
        .formatted(cust);
  }

  /** SC-001 end to end: ‑25% then ‑5% on 1000 → 712.50 with a reconciling breakdown. */
  @Test
  void computeAppliesTheCascadeAndReturnsABreakdown() throws Exception {
    rule("P25", "PROMOTIONAL", "0.25", 1);
    rule("P5", "PROMOTIONAL", "0.05", 2);

    mockMvc
        .perform(
            post("/api/crm/discounts/compute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(compute(null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseTotal").value(1000.00))
        .andExpect(jsonPath("$.finalPrice").value(712.50))
        .andExpect(jsonPath("$.stackingMode").value("MOST_FAVORABLE"))
        .andExpect(jsonPath("$.breakdown.length()").value(2))
        .andExpect(jsonPath("$.breakdown[0].tierCode").value("P25"))
        .andExpect(jsonPath("$.breakdown[0].amountRemoved").value(250.00))
        .andExpect(jsonPath("$.breakdown[1].baseApplied").value(750.00))
        .andExpect(jsonPath("$.breakdown[1].amountRemoved").value(37.50))
        .andExpect(jsonPath("$.flags.length()").value(0));
  }

  /** FR-004: a walk-in (no customer) is priced at base when no promotional rules exist. */
  @Test
  void walkInWithNoRulesPaysBasePrice() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/discounts/compute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(compute(null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalPrice").value(1000.00))
        .andExpect(jsonPath("$.breakdown.length()").value(0));
  }

  /** An unknown customer id is a walk-in, not an error. */
  @Test
  void unknownCustomerIsTreatedAsWalkInNotAnError() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/discounts/compute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(compute("00000000-0000-0000-0000-000000000000")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalPrice").value(1000.00));
  }

  @Test
  void computeWithNoLineItemsReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/discounts/compute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"saleDate\":\"2026-03-01\",\"lineItems\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ruleIsCreatedAndListed() throws Exception {
    rule("P10", "PROMOTIONAL", "0.10", 1);
    mockMvc
        .perform(get("/api/crm/discount-rules").param("asOf", "2026-06-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("P10"));
  }

  @Test
  void duplicateRuleVersionReturns409() throws Exception {
    rule("P10", "PROMOTIONAL", "0.10", 1);
    mockMvc
        .perform(
            post("/api/crm/discount-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"P10","origin":"PROMOTIONAL","computation":"PERCENT","value":0.15,
                     "priority":1,"effectiveDate":"2026-01-01"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void policyDefaultsToMostFavorableAndCanBeChanged() throws Exception {
    mockMvc
        .perform(get("/api/crm/discount-policy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("MOST_FAVORABLE"));

    mockMvc
        .perform(
            put("/api/crm/discount-policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"STATUTORY_ONLY\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("STATUTORY_ONLY"));

    mockMvc
        .perform(get("/api/crm/discount-policy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("STATUTORY_ONLY"));
  }
}
