package com.kita.operations.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kita.operations.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 012 US4 (FR-015): GET /builds lists builds; GET /builds/{id} returns one or 404. A full build round-trip
 * (needs an active manufactured BOM + component stock) is covered by BuildIntegrationTest; here we assert
 * the read endpoints exist and are empty-safe / 404 correct.
 */
class BuildReadContractTest extends AbstractIntegrationTest {

  @Test
  void listIsEmptyWhenNone() throws Exception {
    mockMvc
        .perform(get("/api/operations/builds"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void getReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/operations/builds/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
