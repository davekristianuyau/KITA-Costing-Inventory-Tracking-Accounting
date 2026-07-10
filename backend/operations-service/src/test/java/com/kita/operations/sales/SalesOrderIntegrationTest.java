package com.kita.operations.sales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T023/T024/T026: sales-order lifecycle, reservation effects, oversell + invalid-customer rejection. */
class SalesOrderIntegrationTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  private String seedItemWithStock(String sku, int qty) throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"pcs\",\"family\":\"COUNT\"}"));
    String itemId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"sku\":\"" + sku + "\",\"name\":\"W\",\"type\":\"FINISHED_GOOD\",\"baseUom\":\"pcs\"}"))
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
        post("/api/operations/adjustments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"itemId\":\"" + itemId + "\",\"locationId\":\"" + locId + "\",\"quantity\":" + qty
                    + ",\"reason\":\"seed\"}"));
    return itemId;
  }

  private String createOrder(String customerRef, String itemId, int qty) throws Exception {
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/operations/sales-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"customerRef\":\"" + customerRef + "\",\"lines\":[{\"itemId\":\"" + itemId
                                + "\",\"quantity\":" + qty + ",\"unitPrice\":9.99}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
        .get("id").asText();
  }

  @Test
  void confirmReservesFulfillDecrementsCancelReleases() throws Exception {
    String itemId = seedItemWithStock("SKU-SALE", 10);

    String orderId = createOrder("acme", itemId, 3);
    mockMvc.perform(post("/api/operations/sales-orders/" + orderId + "/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    // reserved 3 of 10 → available 7
    mockMvc.perform(get("/api/operations/items/" + itemId + "/availability"))
        .andExpect(jsonPath("$[0].onHand").value(10))
        .andExpect(jsonPath("$[0].reserved").value(3))
        .andExpect(jsonPath("$[0].available").value(7));

    mockMvc.perform(post("/api/operations/sales-orders/" + orderId + "/fulfill"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FULFILLED"));
    // fulfilled → on-hand 7, reserved 0
    mockMvc.perform(get("/api/operations/items/" + itemId + "/availability"))
        .andExpect(jsonPath("$[0].onHand").value(7))
        .andExpect(jsonPath("$[0].reserved").value(0));

    // a second order, confirmed then cancelled, restores availability
    String order2 = createOrder("acme", itemId, 2);
    mockMvc.perform(post("/api/operations/sales-orders/" + order2 + "/confirm"))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/operations/sales-orders/" + order2 + "/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mockMvc.perform(get("/api/operations/items/" + itemId + "/availability"))
        .andExpect(jsonPath("$[0].available").value(7));
  }

  @Test
  void confirmingMoreThanAvailableIsRejected() throws Exception {
    String itemId = seedItemWithStock("SKU-OVER", 5);
    String orderId = createOrder("acme", itemId, 10);
    mockMvc.perform(post("/api/operations/sales-orders/" + orderId + "/confirm"))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidCustomerIsRejected() throws Exception {
    String itemId = seedItemWithStock("SKU-CUST", 5);
    mockMvc.perform(
            post("/api/operations/sales-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerRef\":\"invalid\",\"lines\":[{\"itemId\":\"" + itemId
                        + "\",\"quantity\":1,\"unitPrice\":1.00}]}"))
        .andExpect(status().isBadRequest());
  }
}
