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

/** 012 US4 (FR-015): GET /sales-orders lists orders; GET /sales-orders/{id} returns one or 404. */
class SalesOrderReadContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  private String createItem() throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ea\",\"family\":\"COUNT\"}"));
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/operations/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"sku\":\"SKU-SO\",\"name\":\"Sellable\",\"type\":\"FINISHED_GOOD\",\"baseUom\":\"ea\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asText();
  }

  @Test
  void listAndGetReturnCreatedOrder() throws Exception {
    String itemId = createItem();
    String orderId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/sales-orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"customerRef\":\"CUST-1\",\"lines\":[{\"itemId\":\""
                                    + itemId
                                    + "\",\"quantity\":3,\"unitPrice\":10.00}]}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(get("/api/operations/sales-orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(orderId))
        .andExpect(jsonPath("$[0].customerRef").value("CUST-1"))
        .andExpect(jsonPath("$[0].status").exists());

    mockMvc
        .perform(get("/api/operations/sales-orders/" + orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.lines[0].itemId").value(itemId));
  }

  @Test
  void getReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/operations/sales-orders/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
