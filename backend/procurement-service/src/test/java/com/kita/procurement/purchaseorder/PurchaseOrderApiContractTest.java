package com.kita.procurement.purchaseorder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.procurement.support.AbstractProcurementIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T017: contract tests for /purchase-orders and its approve/send/cancel transitions. */
class PurchaseOrderApiContractTest extends AbstractProcurementIT {

  @Autowired private ObjectMapper mapper;

  private String supplier() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/procurement/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"supplierCode\":\"S-PO\",\"name\":\"Acme\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  /** Two lines: 10 × 25.50 = 255.00 and 3 × 100.00 = 300.00 → order total 555.00. */
  private String twoLinePo(String supplierId) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/procurement/purchase-orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"supplierId":"%s","lines":[
                          {"itemRef":"ITEM-1","qtyOrdered":10,"agreedPrice":25.50},
                          {"itemRef":"ITEM-2","qtyOrdered":3,"agreedPrice":100.00}]}
                        """
                            .formatted(supplierId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  @Test
  void createComputesLineAndOrderTotals() throws Exception {
    String s = supplier();
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"supplierId":"%s","lines":[
                      {"itemRef":"ITEM-1","qtyOrdered":10,"agreedPrice":25.50},
                      {"itemRef":"ITEM-2","qtyOrdered":3,"agreedPrice":100.00}]}
                    """
                        .formatted(s)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.origin").value("MANUAL"))
        .andExpect(jsonPath("$.poNo").isNotEmpty())
        .andExpect(jsonPath("$.orderTotal").value(555.00))
        .andExpect(jsonPath("$.lines.length()").value(2))
        .andExpect(jsonPath("$.lines[0].lineTotal").value(255.00))
        .andExpect(jsonPath("$.lines[0].qtyOutstanding").value(10));
  }

  @Test
  void fullLifecycleDraftToApprovedToSent() throws Exception {
    String id = twoLinePo(supplier());

    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty());

    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/send"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
  }

  @Test
  void sendBeforeApproveIsRejected() throws Exception {
    String id = twoLinePo(supplier());
    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/send"))
        .andExpect(status().isConflict());
  }

  @Test
  void doubleApproveIsRejected() throws Exception {
    String id = twoLinePo(supplier());
    mockMvc.perform(post("/api/procurement/purchase-orders/" + id + "/approve")).andExpect(status().isOk());
    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/approve"))
        .andExpect(status().isConflict());
  }

  @Test
  void cancelBeforeReceiptSucceedsAndThenNothingElseIsAllowed() throws Exception {
    String id = twoLinePo(supplier());
    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(post("/api/procurement/purchase-orders/" + id + "/approve"))
        .andExpect(status().isConflict());
  }

  @Test
  void unknownSupplierReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"supplierId":"00000000-0000-0000-0000-000000000000",
                     "lines":[{"itemRef":"ITEM-1","qtyOrdered":1,"agreedPrice":10.00}]}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void orderWithNoLinesReturns400() throws Exception {
    String s = supplier();
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":\"%s\",\"lines\":[]}".formatted(s)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void negativeQuantityReturns400() throws Exception {
    String s = supplier();
    mockMvc
        .perform(
            post("/api/procurement/purchase-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"supplierId":"%s","lines":[{"itemRef":"ITEM-1","qtyOrdered":-5,"agreedPrice":10.00}]}
                    """
                        .formatted(s)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getAndListReturnTheOrder() throws Exception {
    String id = twoLinePo(supplier());
    mockMvc
        .perform(get("/api/procurement/purchase-orders/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
    mockMvc
        .perform(get("/api/procurement/purchase-orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void unknownOrderReturns404() throws Exception {
    mockMvc
        .perform(get("/api/procurement/purchase-orders/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }
}
