package com.kita.operations.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T043: goods-receipt endpoint conforms and increases stock. */
class ReceiptContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  @Test
  void postReceiptIncreasesAvailability() throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ea\",\"family\":\"COUNT\"}"));
    String itemId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sku\":\"SKU-RC\",\"name\":\"W\",\"type\":\"RAW_MATERIAL\",\"baseUom\":\"ea\"}"))
                    .andReturn().getResponse().getContentAsString())
            .get("id").asText();
    String locId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/locations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"WH\",\"name\":\"WH\"}"))
                    .andReturn().getResponse().getContentAsString())
            .get("id").asText();

    mockMvc.perform(
            post("/api/operations/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"supplierRef\":\"acme\",\"locationId\":\"" + locId + "\",\"lines\":[{\"itemId\":\""
                        + itemId + "\",\"quantity\":12,\"unitCost\":3.50}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists());

    mockMvc.perform(get("/api/operations/items/" + itemId + "/availability"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].onHand").value(12));
  }
}
