package com.kita.operations.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kita.operations.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 012 US2 (FR-015): GET /locations lists the client's stock locations (empty when none). */
class LocationReadContractTest extends AbstractIntegrationTest {

  @Test
  void listLocationsReturnsCreatedLocations() throws Exception {
    mockMvc.perform(
        post("/api/operations/locations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"WH1\",\"name\":\"Warehouse 1\"}"));

    mockMvc
        .perform(get("/api/operations/locations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("WH1"))
        .andExpect(jsonPath("$[0].name").value("Warehouse 1"))
        .andExpect(jsonPath("$[0].id").exists());
  }

  @Test
  void listLocationsIsEmptyWhenNone() throws Exception {
    mockMvc
        .perform(get("/api/operations/locations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
