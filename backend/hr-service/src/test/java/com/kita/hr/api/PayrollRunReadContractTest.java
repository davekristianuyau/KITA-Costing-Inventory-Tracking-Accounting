package com.kita.hr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.support.AbstractHrIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 013 US3 (FR-015): GET /payroll/runs lists runs; GET /payroll/runs/{id} returns one or 404. */
class PayrollRunReadContractTest extends AbstractHrIT {

  private static final String PERIOD =
      "{\"period\":{\"frequency\":\"MONTHLY\",\"startDate\":\"2026-03-01\","
          + "\"endDate\":\"2026-03-31\",\"payDate\":\"2026-03-31\"}}";

  @Autowired private ObjectMapper mapper;

  private void seedEmployee() throws Exception {
    mockMvc.perform(
        post("/api/hr/employees")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"employeeNo":"PR-001","firstName":"Pay","lastName":"Run",
                 "employmentType":"REGULAR","dateHired":"2025-01-01"}
                """));
  }

  @Test
  void listAndGetReturnCreatedRun() throws Exception {
    seedEmployee();
    String runId =
        mapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/hr/payroll/runs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PERIOD))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();

    mockMvc
        .perform(get("/api/hr/payroll/runs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(runId))
        .andExpect(jsonPath("$[0].status").exists())
        .andExpect(jsonPath("$[0].periodStart").value("2026-03-01"));

    mockMvc
        .perform(get("/api/hr/payroll/runs/" + runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(runId))
        .andExpect(jsonPath("$.payDate").value("2026-03-31"));
  }

  @Test
  void getReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/hr/payroll/runs/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
