package com.kita.operations.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 012 US4 (FR-015): GET /receipts lists goods receipts (with lines + receivedAt); GET /receipts/{id}
 * returns one or 404.
 */
class GoodsReceiptReadContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  @Test
  void listAndGetReturnPostedReceipt() throws Exception {
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
                            .content(
                                "{\"sku\":\"SKU-GR\",\"name\":\"Raw\",\"type\":\"RAW_MATERIAL\",\"baseUom\":\"ea\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    String locId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/locations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"WH1\",\"name\":\"Warehouse 1\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    String receiptId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/receipts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"supplierRef\":\"SUP-1\",\"locationId\":\""
                                    + locId
                                    + "\",\"lines\":[{\"itemId\":\""
                                    + itemId
                                    + "\",\"quantity\":5,\"unitCost\":2.50}]}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(get("/api/operations/receipts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(receiptId))
        .andExpect(jsonPath("$[0].supplierRef").value("SUP-1"))
        .andExpect(jsonPath("$[0].receivedAt").exists())
        .andExpect(jsonPath("$[0].lines[0].itemId").value(itemId));

    mockMvc
        .perform(get("/api/operations/receipts/" + receiptId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(receiptId))
        .andExpect(jsonPath("$.lines[0].quantity").value(5));
  }

  @Test
  void getReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/operations/receipts/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
