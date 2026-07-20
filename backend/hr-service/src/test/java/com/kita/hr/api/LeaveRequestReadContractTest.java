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

/** 013 US2 (FR-015): GET /leave/requests lists requests (with employeeId/status filters); /{id} → one or 404. */
class LeaveRequestReadContractTest extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private String createEmployee(String no) throws Exception {
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/hr/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"employeeNo":"%s","firstName":"Req","lastName":"Read",
                             "employmentType":"REGULAR","dateHired":"2025-01-01"}
                            """.formatted(no)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asText();
  }

  private String createLeaveType(String code) throws Exception {
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/hr/leave/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"code":"%s","name":"%s Leave","payTreatment":"PAID",
                             "accrualRate":1.25,"accrualPeriod":"MONTHLY","allowNegative":true}
                            """.formatted(code, code)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asText();
  }

  private String fileRequest(String empId, String typeId) throws Exception {
    return mapper
        .readTree(
            mockMvc
                .perform(
                    post("/api/hr/leave/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"employeeId":"%s","leaveTypeId":"%s",
                             "startDate":"2026-08-04","endDate":"2026-08-05","duration":2}
                            """.formatted(empId, typeId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asText();
  }

  @Test
  void listAndGetReturnFiledRequest() throws Exception {
    String empId = createEmployee("RR-001");
    String typeId = createLeaveType("RR1");
    String reqId = fileRequest(empId, typeId);

    mockMvc
        .perform(get("/api/hr/leave/requests"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(reqId))
        .andExpect(jsonPath("$[0].employeeId").value(empId))
        .andExpect(jsonPath("$[0].status").value("FILED"));

    mockMvc
        .perform(get("/api/hr/leave/requests").param("employeeId", empId).param("status", "FILED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(reqId));

    mockMvc
        .perform(get("/api/hr/leave/requests/" + reqId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reqId))
        .andExpect(jsonPath("$.duration").value(2));
  }

  @Test
  void getReturns404WhenAbsent() throws Exception {
    mockMvc
        .perform(get("/api/hr/leave/requests/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }
}
