package com.kita.operations.bom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T032: BOM create + explosion endpoints conform to the contract. */
class BomContractTest extends AbstractIntegrationTest {

  @Autowired ObjectMapper mapper;

  private String createItem(String sku) throws Exception {
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/operations/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"sku\":\"" + sku + "\",\"name\":\"" + sku + "\",\"type\":\"COMPONENT\",\"baseUom\":\"ea\"}"))
                .andReturn().getResponse().getContentAsString())
        .get("id").asText();
  }

  @Test
  void createBomAndExplode() throws Exception {
    mockMvc.perform(
        post("/api/operations/uoms")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ea\",\"family\":\"COUNT\"}"));
    String parent = createItem("PARENT");
    String comp = createItem("COMP");

    mockMvc.perform(
            post("/api/operations/boms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"parentItemId\":\"" + parent + "\",\"type\":\"MANUFACTURED\",\"components\":"
                        + "[{\"componentItemId\":\"" + comp + "\",\"quantity\":2,\"uom\":\"ea\"}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists());

    mockMvc.perform(get("/api/operations/boms/" + parent + "/explosion").param("quantity", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].componentItemId").value(comp))
        .andExpect(jsonPath("$[0].requiredQuantity").value(6)); // 2 * 3
  }
}
