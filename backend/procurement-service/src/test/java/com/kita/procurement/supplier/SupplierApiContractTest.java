package com.kita.procurement.supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.procurement.support.AbstractProcurementIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T011: contract tests for /suppliers, /suppliers/{id}, /suppliers/{id}/items. */
class SupplierApiContractTest extends AbstractProcurementIT {

  @Autowired private ObjectMapper mapper;

  private static final String VALID =
      """
      {"supplierCode":"S-001","name":"Acme Trading","email":"acme@example.com",
       "paymentTerms":"NET30","deliveryTerms":"FOB"}
      """;

  private String create() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/procurement/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  @Test
  void createReturns201WithActiveStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/procurement/suppliers").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.supplierCode").value("S-001"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void duplicateSupplierCodeReturns409() throws Exception {
    create();
    mockMvc
        .perform(
            post("/api/procurement/suppliers").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isConflict());
  }

  @Test
  void missingRequiredFieldReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/procurement/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"No Code\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUnknownReturns404() throws Exception {
    mockMvc
        .perform(get("/api/procurement/suppliers/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getReturnsThePartyLookupPayload() throws Exception {
    String id = create();
    mockMvc
        .perform(get("/api/procurement/suppliers/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.supplierCode").value("S-001"))
        .andExpect(jsonPath("$.paymentTerms").value("NET30"));
  }

  @Test
  void patchUpdatesAndListReturnsSuppliers() throws Exception {
    String id = create();
    mockMvc
        .perform(
            patch("/api/procurement/suppliers/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"status\":\"INACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme Corp"))
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    mockMvc
        .perform(get("/api/procurement/suppliers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void suppliedItemsAreAddedAndListed() throws Exception {
    String id = create();
    mockMvc
        .perform(
            post("/api/procurement/suppliers/" + id + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"itemRef":"ITEM-1","supplierPrice":25.50,"leadTimeDays":7,
                     "minOrderQty":10,"preferred":true}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.itemRef").value("ITEM-1"))
        .andExpect(jsonPath("$.supplierPrice").value(25.50))
        .andExpect(jsonPath("$.preferred").value(true));

    mockMvc
        .perform(get("/api/procurement/suppliers/" + id + "/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void itemForUnknownSupplierReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/procurement/suppliers/00000000-0000-0000-0000-000000000000/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemRef\":\"ITEM-1\",\"supplierPrice\":10.00}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void negativeSupplierPriceReturns400() throws Exception {
    String id = create();
    mockMvc
        .perform(
            post("/api/procurement/suppliers/" + id + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemRef\":\"ITEM-1\",\"supplierPrice\":-5.00}"))
        .andExpect(status().isBadRequest());
  }
}
