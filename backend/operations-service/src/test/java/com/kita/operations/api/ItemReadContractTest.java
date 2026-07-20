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

/** 012 US1 (FR-015): GET /items/{id} returns the item, or 404 when absent. */
class ItemReadContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  @Test
  void getItemByIdReturnsTheItem() throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ea\",\"family\":\"COUNT\"}"));
    String id =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/operations/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"sku\":\"SKU-DETAIL\",\"name\":\"Widget\",\"type\":\"FINISHED_GOOD\",\"baseUom\":\"ea\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(get("/api/operations/items/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.sku").value("SKU-DETAIL"))
        .andExpect(jsonPath("$.name").value("Widget"));
  }

  @Test
  void getItemByIdReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/operations/items/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
