package com.kita.operations.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T063: movement period query returns valuation-ready data (qty + unit cost). */
class MovementDataContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  @Test
  void periodQueryReturnsMovementsWithCost() throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms").contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ea\",\"family\":\"COUNT\"}"));
    String itemId =
        mapper.readTree(
                mockMvc.perform(post("/api/operations/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SKU-MV\",\"name\":\"W\",\"type\":\"RAW_MATERIAL\",\"baseUom\":\"ea\"}"))
                    .andReturn().getResponse().getContentAsString())
            .get("id").asText();
    String locId =
        mapper.readTree(
                mockMvc.perform(post("/api/operations/locations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WH\",\"name\":\"WH\"}"))
                    .andReturn().getResponse().getContentAsString())
            .get("id").asText();
    mockMvc.perform(post("/api/operations/receipts").contentType(MediaType.APPLICATION_JSON)
        .content("{\"supplierRef\":\"acme\",\"locationId\":\"" + locId + "\",\"lines\":[{\"itemId\":\""
            + itemId + "\",\"quantity\":10,\"unitCost\":5}]}"));

    mockMvc.perform(get("/api/operations/movements")
            .param("itemId", itemId)
            .param("from", "2000-01-01T00:00:00Z")
            .param("to", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("RECEIPT"))
        .andExpect(jsonPath("$[0].quantity").value(10))
        .andExpect(jsonPath("$[0].unitCost").value(5));
  }
}
