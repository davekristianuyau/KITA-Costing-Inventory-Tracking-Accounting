package com.kita.hr.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.support.AbstractHrIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T040: contract test for attendance ingest + worked-time computation. */
class AttendanceApiContractTest extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private String createEmployee() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeNo":"E-A1","firstName":"At","lastName":"Tend",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  @Test
  void ingestThenComputeWorkedTime() throws Exception {
    String id = createEmployee();

    mockMvc
        .perform(
            post("/api/hr/work-schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("{\"employeeId\":\"%s\",\"effectiveDate\":\"2026-01-01\",\"shiftStart\":\"08:00\","
                            + "\"shiftEnd\":\"17:00\",\"breakMinutes\":60,\"standardDailyHours\":8.00,"
                            + "\"nightStart\":\"22:00\",\"nightEnd\":\"06:00\"}")
                        .formatted(id)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/hr/attendance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    ("[{\"employeeId\":\"%s\",\"workDate\":\"2026-01-05\",\"timeIn\":\"08:00\","
                            + "\"timeOut\":\"19:00\"}]")
                        .formatted(id)))
        .andExpect(status().isAccepted());

    mockMvc
        .perform(
            get("/api/hr/attendance/worked-time")
                .param("employeeId", id)
                .param("start", "2026-01-05")
                .param("end", "2026-01-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scheduled").value(true))
        .andExpect(jsonPath("$.incomplete").value(false))
        .andExpect(jsonPath("$.overtimeHours").value(2.00))
        .andExpect(jsonPath("$.regularHours").value(8.00));
  }
}
