package com.kita.procurement.receiving;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.procurement.support.AbstractProcurementIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T025: contract tests for /purchase-orders/{id}/receipts. */
class ReceiptApiContractTest extends AbstractProcurementIT {

  @Autowired private ObjectMapper mapper;

  /** A SENT order for 10 × ITEM-1 @ 25.00. */
  private String sentOrder(String code) throws Exception {
    String sJson =
        mockMvc
            .perform(
                post("/api/procurement/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"supplierCode\":\"%s\",\"name\":\"Acme\"}".formatted(code)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String supplierId = mapper.readTree(sJson).get("id").asText();

    String poJson =
        mockMvc
            .perform(
                post("/api/procurement/purchase-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"supplierId":"%s","lines":[
                          {"itemRef":"ITEM-1","qtyOrdered":10,"agreedPrice":25.00}]}
                        """
                            .formatted(supplierId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(poJson).get("id").asText();

    mockMvc.perform(post("/api/procurement/purchase-orders/" + id + "/approve")).andExpect(status().isOk());
    mockMvc.perform(post("/api/procurement/purchase-orders/" + id + "/send")).andExpect(status().isOk());
    return id;
  }

  private String receipt(String qty) {
    return "{\"lines\":[{\"itemRef\":\"ITEM-1\",\"qtyReceived\":%s}]}".formatted(qty);
  }

  @Test
  void recordingAFullReceiptReturns201AndAdvancesTheOrder() throws Exception {
    String id = sentOrder("RA-1");

    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("10")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.postedToOperations").value(true))
        .andExpect(jsonPath("$.orderStatus").value("FULLY_RECEIVED"))
        .andExpect(jsonPath("$.lines[0].qtyReceived").value(10))
        .andExpect(jsonPath("$.lines[0].unitCost").value(25.00));
  }

  @Test
  void partialReceiptLeavesTheOrderPartiallyReceived() throws Exception {
    String id = sentOrder("RA-2");

    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("4")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderStatus").value("PARTIALLY_RECEIVED"));
  }

  @Test
  void overReceiptReturns409() throws Exception {
    String id = sentOrder("RA-3");

    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("11")))
        .andExpect(status().isConflict());
  }

  /** Receiving a DRAFT order is the headline illegal transition. */
  @Test
  void receivingADraftOrderReturns409() throws Exception {
    String sJson =
        mockMvc
            .perform(
                post("/api/procurement/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"supplierCode\":\"RA-4\",\"name\":\"Acme\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String supplierId = mapper.readTree(sJson).get("id").asText();
    String poJson =
        mockMvc
            .perform(
                post("/api/procurement/purchase-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"supplierId":"%s","lines":[{"itemRef":"ITEM-1","qtyOrdered":10,"agreedPrice":25.00}]}
                        """
                            .formatted(supplierId)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String draftId = mapper.readTree(poJson).get("id").asText();

    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + draftId + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("1")))
        .andExpect(status().isConflict());
  }

  @Test
  void receiptsAreListedForTheOrder() throws Exception {
    String id = sentOrder("RA-5");
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("4")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/procurement/purchase-orders/" + id + "/receipts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void closeAfterFullReceiptReturnsClosed() throws Exception {
    String id = sentOrder("RA-6");
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receipt("10")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  @Test
  void emptyReceiptReturns400() throws Exception {
    String id = sentOrder("RA-7");
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders/" + id + "/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[]}"))
        .andExpect(status().isBadRequest());
  }
}
