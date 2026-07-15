package com.kita.crm.loyalty;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.crm.support.AbstractCrmIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T025: contract tests for /loyalty/tiers and /customers/{id}/loyalty/evaluate. */
class LoyaltyApiContractTest extends AbstractCrmIT {

  @Autowired private ObjectMapper mapper;

  private String createRule() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/crm/discount-rules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"LOY5","origin":"LOYALTY","computation":"PERCENT","value":0.05,
                         "priority":10,"effectiveDate":"2026-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  private String createCustomer() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/crm/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"customerCode":"LC-1","type":"INDIVIDUAL","name":"Ana"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  private void createTier(String ruleId) throws Exception {
    mockMvc
        .perform(
            post("/api/crm/loyalty/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"SILVER","name":"Silver","minPurchaseCount":5,
                     "minPurchaseValue":10000,"periodDays":365,"discountRuleId":"%s"}
                    """
                        .formatted(ruleId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SILVER"));
  }

  @Test
  void tierIsCreatedAndListed() throws Exception {
    createTier(createRule());
    mockMvc
        .perform(get("/api/crm/loyalty/tiers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("SILVER"));
  }

  @Test
  void tierForUnknownRuleReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/loyalty/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"GHOST","name":"Ghost","minPurchaseCount":1,
                     "discountRuleId":"00000000-0000-0000-0000-000000000000"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void duplicateTierCodeReturns409() throws Exception {
    String ruleId = createRule();
    createTier(ruleId);
    mockMvc
        .perform(
            post("/api/crm/loyalty/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"SILVER","name":"Dup","minPurchaseCount":5,"discountRuleId":"%s"}
                    """
                        .formatted(ruleId)))
        .andExpect(status().isConflict());
  }

  @Test
  void evaluateAssignsTheTierForQualifyingActivity() throws Exception {
    createTier(createRule());
    String id = createCustomer();

    mockMvc
        .perform(
            post("/api/crm/customers/" + id + "/loyalty/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchaseCount\":6,\"purchaseValue\":12000}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SILVER"));

    mockMvc
        .perform(get("/api/crm/customers/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loyaltyTierId").isNotEmpty());
  }

  @Test
  void evaluateForUnknownCustomerReturns404() throws Exception {
    createTier(createRule());
    mockMvc
        .perform(
            post("/api/crm/customers/00000000-0000-0000-0000-000000000000/loyalty/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchaseCount\":6,\"purchaseValue\":12000}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void evaluateWithNegativeActivityReturns400() throws Exception {
    String id = createCustomer();
    mockMvc
        .perform(
            post("/api/crm/customers/" + id + "/loyalty/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchaseCount\":-1,\"purchaseValue\":100}"))
        .andExpect(status().isBadRequest());
  }
}
