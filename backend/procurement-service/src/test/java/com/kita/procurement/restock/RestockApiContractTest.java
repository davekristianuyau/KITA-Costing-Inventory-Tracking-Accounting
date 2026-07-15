package com.kita.procurement.restock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.procurement.operations.OperationsPort;
import com.kita.procurement.support.AbstractProcurementIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T032: contract tests for /restock/suggestions (generate, list, convert, dismiss). */
class RestockApiContractTest extends AbstractProcurementIT {

  @Autowired private ObjectMapper mapper;

  /** A supplier who is the preferred source for ITEM-1 @ 25.00, plus a low-stock signal for it. */
  private void seedLowStockItem(String supplierCode) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/procurement/suppliers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"supplierCode\":\"%s\",\"name\":\"Acme\"}".formatted(supplierCode)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String supplierId = mapper.readTree(json).get("id").asText();

    mockMvc
        .perform(
            post("/api/procurement/suppliers/" + supplierId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"itemRef":"ITEM-1","supplierPrice":25.00,"leadTimeDays":7,"preferred":true}
                    """))
        .andExpect(status().isCreated());

    operations.seedSignal(
        new OperationsPort.ReorderSignal(
            "ITEM-1", new BigDecimal("2"), new BigDecimal("5"), new BigDecimal("10")));
  }

  private String generateOne() throws Exception {
    String json =
        mockMvc
            .perform(post("/api/procurement/restock/suggestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get(0).get("id").asText();
  }

  @Test
  void generateProducesASizedSuggestion() throws Exception {
    seedLowStockItem("RAPI-1");

    mockMvc
        .perform(post("/api/procurement/restock/suggestions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("OPEN"))
        .andExpect(jsonPath("$[0].lines.length()").value(1))
        .andExpect(jsonPath("$[0].lines[0].itemRef").value("ITEM-1"))
        .andExpect(jsonPath("$[0].lines[0].suggestedQty").value(8))
        .andExpect(jsonPath("$[0].lines[0].onHand").value(2))
        .andExpect(jsonPath("$[0].lines[0].targetLevel").value(10));
  }

  @Test
  void generateWithNoSignalsProducesNothing() throws Exception {
    mockMvc
        .perform(post("/api/procurement/restock/suggestions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void openSuggestionsAreListed() throws Exception {
    seedLowStockItem("RAPI-2");
    generateOne();

    mockMvc
        .perform(get("/api/procurement/restock/suggestions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("OPEN"));
  }

  /** FR-014: converting yields a DRAFT PO, not a sent one. */
  @Test
  void convertReturnsADraftPurchaseOrder() throws Exception {
    seedLowStockItem("RAPI-3");
    String id = generateOne();

    mockMvc
        .perform(post("/api/procurement/restock/suggestions/" + id + "/convert"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.origin").value("RESTOCK"))
        .andExpect(jsonPath("$.orderTotal").value(200.00))
        .andExpect(jsonPath("$.lines[0].qtyOrdered").value(8));
  }

  @Test
  void convertingTwiceReturns409() throws Exception {
    seedLowStockItem("RAPI-4");
    String id = generateOne();
    mockMvc.perform(post("/api/procurement/restock/suggestions/" + id + "/convert")).andExpect(status().isOk());

    mockMvc
        .perform(post("/api/procurement/restock/suggestions/" + id + "/convert"))
        .andExpect(status().isConflict());
  }

  @Test
  void dismissRemovesItFromTheOpenList() throws Exception {
    seedLowStockItem("RAPI-5");
    String id = generateOne();

    mockMvc
        .perform(post("/api/procurement/restock/suggestions/" + id + "/dismiss"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));

    mockMvc
        .perform(get("/api/procurement/restock/suggestions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void convertingAnUnknownSuggestionReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/procurement/restock/suggestions/00000000-0000-0000-0000-000000000000/convert"))
        .andExpect(status().isNotFound());
  }
}
