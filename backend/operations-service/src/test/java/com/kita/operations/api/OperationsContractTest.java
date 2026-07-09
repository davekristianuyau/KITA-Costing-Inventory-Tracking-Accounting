package com.kita.operations.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T012: endpoints conform to the OpenAPI shape (fields present, correct statuses). */
class OperationsContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  @Test
  void fullInventoryRoundTripThroughApi() throws Exception {
    // health
    mockMvc.perform(get("/api/operations/health")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));

    // create UoM
    mockMvc
        .perform(
            post("/api/operations/uoms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"pcs\",\"family\":\"COUNT\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("pcs"));

    // create item
    String itemJson =
        mockMvc
            .perform(
                post("/api/operations/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"sku\":\"SKU-API\",\"name\":\"Bolt\",\"type\":\"FINISHED_GOOD\",\"baseUom\":\"pcs\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.sku").value("SKU-API"))
            .andExpect(jsonPath("$.valuationMethod").value("AVCO"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode item = mapper.readTree(itemJson);
    String itemId = item.get("id").asText();

    // create location
    String locJson =
        mockMvc
            .perform(
                post("/api/operations/locations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"WH1\",\"name\":\"Warehouse 1\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String locId = mapper.readTree(locJson).get("id").asText();

    // adjustment (seed stock)
    mockMvc
        .perform(
            post("/api/operations/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"itemId\":\""
                        + itemId
                        + "\",\"locationId\":\""
                        + locId
                        + "\",\"quantity\":7,\"reason\":\"seed\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("ADJUSTMENT"))
        .andExpect(jsonPath("$.quantity").value(7));

    // availability
    mockMvc
        .perform(get("/api/operations/items/" + itemId + "/availability"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].onHand").value(7))
        .andExpect(jsonPath("$[0].available").value(7));

    // movements
    mockMvc
        .perform(get("/api/operations/movements").param("itemId", itemId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].type").value("ADJUSTMENT"));
  }

  @Test
  void rejectsOverIssueWith409() throws Exception {
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
                                "{\"sku\":\"SKU-NEG\",\"name\":\"N\",\"type\":\"FINISHED_GOOD\",\"baseUom\":\"ea\"}"))
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
                            .content("{\"code\":\"WH-NEG\",\"name\":\"N\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(
            post("/api/operations/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"itemId\":\""
                        + itemId
                        + "\",\"locationId\":\""
                        + locId
                        + "\",\"quantity\":-5,\"reason\":\"over\"}"))
        .andExpect(status().isConflict());
  }
}
