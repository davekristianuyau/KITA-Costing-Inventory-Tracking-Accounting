package com.kita.crm.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.crm.support.AbstractCrmIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T011: contract tests for /customers, /customers/{id}, /customers/{id}/entitlements. */
class CustomerApiContractTest extends AbstractCrmIT {

  @Autowired private ObjectMapper mapper;

  private static final String VALID =
      """
      {"customerCode":"C-001","type":"INDIVIDUAL","name":"Ana Cruz",
       "email":"ana@example.com","phone":"0917","address":"Cebu"}
      """;

  private String create(String body) throws Exception {
    String json =
        mockMvc
            .perform(post("/api/crm/customers").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  @Test
  void createReturns201WithActiveStatus() throws Exception {
    mockMvc
        .perform(post("/api/crm/customers").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.customerCode").value("C-001"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void duplicateCustomerCodeReturns409() throws Exception {
    create(VALID);
    mockMvc
        .perform(post("/api/crm/customers").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isConflict());
  }

  @Test
  void missingRequiredFieldReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No Code\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUnknownReturns404() throws Exception {
    mockMvc
        .perform(get("/api/crm/customers/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getReturnsThePartyLookupPayload() throws Exception {
    String id = create(VALID);
    mockMvc
        .perform(get("/api/crm/customers/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.customerCode").value("C-001"))
        .andExpect(jsonPath("$.type").value("INDIVIDUAL"));
  }

  @Test
  void patchUpdatesAndListReturnsCustomers() throws Exception {
    String id = create(VALID);
    mockMvc
        .perform(
            patch("/api/crm/customers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana Reyes\",\"status\":\"INACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ana Reyes"))
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    mockMvc.perform(get("/api/crm/customers")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void entitlementIsCreatedAndItsSupportingIdIsNeverReturned() throws Exception {
    String id = create(VALID);
    String body =
        """
        {"kind":"SENIOR","supportingIdRef":"SC-123456789","validFrom":"2026-01-01"}
        """;
    String created =
        mockMvc
            .perform(
                post("/api/crm/customers/" + id + "/entitlements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("SENIOR"))
            .andExpect(jsonPath("$.supportingIdOnFile").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(created).doesNotContain("SC-123456789");

    String listed =
        mockMvc
            .perform(get("/api/crm/customers/" + id + "/entitlements"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(listed).doesNotContain("SC-123456789");
  }

  @Test
  void entitlementForUnknownCustomerReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/crm/customers/00000000-0000-0000-0000-000000000000/entitlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PWD\",\"validFrom\":\"2026-01-01\"}"))
        .andExpect(status().isNotFound());
  }
}
