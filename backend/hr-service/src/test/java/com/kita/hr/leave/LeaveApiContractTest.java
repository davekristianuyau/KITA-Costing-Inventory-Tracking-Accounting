package com.kita.hr.leave;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.support.AbstractHrIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T046: contract tests for /leave/types, /leave/requests, /decision, /leave/balances. */
class LeaveApiContractTest extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private String createEmployee(String no) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeNo":"%s","firstName":"Leave","lastName":"Test",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """.formatted(no)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  private String createLeaveType(String code, String treatment, double accrualRate,
      boolean allowNeg) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/leave/types")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"code":"%s","name":"%s Leave","payTreatment":"%s",
                         "accrualRate":%s,"accrualPeriod":"MONTHLY","allowNegative":%s}
                        """.formatted(code, code, treatment, accrualRate, allowNeg)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(json).get("id").asText();
  }

  @Test
  void defineLeaveType_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/hr/leave/types")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"VL","name":"Vacation Leave","payTreatment":"PAID",
                     "accrualRate":1.25,"accrualPeriod":"MONTHLY","allowNegative":false}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.code").value("VL"));
  }

  @Test
  void fileLeaveRequest_returns201() throws Exception {
    String empId = createEmployee("LC-001");
    String typeId = createLeaveType("VL2", "PAID", 1.25, true);

    mockMvc
        .perform(
            post("/api/hr/leave/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"employeeId":"%s","leaveTypeId":"%s",
                     "startDate":"2026-08-04","endDate":"2026-08-05","duration":2,
                     "reason":"vacation"}
                    """.formatted(empId, typeId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.status").value("FILED"));
  }

  @Test
  void fileOverlappingLeave_returns409() throws Exception {
    String empId = createEmployee("LC-002");
    String typeId = createLeaveType("VL3", "PAID", 1.25, true);

    // First request filed and approved
    String reqJson =
        mockMvc
            .perform(
                post("/api/hr/leave/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeId":"%s","leaveTypeId":"%s",
                         "startDate":"2026-08-10","endDate":"2026-08-12","duration":3}
                        """.formatted(empId, typeId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reqId = mapper.readTree(reqJson).get("id").asText();

    mockMvc
        .perform(
            post("/api/hr/leave/requests/" + reqId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approved":true,"decidedBy":"manager1"}
                    """))
        .andExpect(status().isOk());

    // Overlapping second request → 409
    mockMvc
        .perform(
            post("/api/hr/leave/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"employeeId":"%s","leaveTypeId":"%s",
                     "startDate":"2026-08-11","endDate":"2026-08-13","duration":3}
                    """.formatted(empId, typeId)))
        .andExpect(status().isConflict());
  }

  @Test
  void decideLeaveRequest_returns200() throws Exception {
    String empId = createEmployee("LC-003");
    String typeId = createLeaveType("SL", "PAID", 1.25, true);

    String reqJson =
        mockMvc
            .perform(
                post("/api/hr/leave/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeId":"%s","leaveTypeId":"%s",
                         "startDate":"2026-09-01","endDate":"2026-09-02","duration":2}
                        """.formatted(empId, typeId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reqId = mapper.readTree(reqJson).get("id").asText();

    mockMvc
        .perform(
            post("/api/hr/leave/requests/" + reqId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approved":true,"decidedBy":"mgr"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void rejectLeaveRequest_returns200WithRejected() throws Exception {
    String empId = createEmployee("LC-004");
    String typeId = createLeaveType("SL2", "PAID", 1.25, false);

    String reqJson =
        mockMvc
            .perform(
                post("/api/hr/leave/requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeId":"%s","leaveTypeId":"%s",
                         "startDate":"2026-09-10","endDate":"2026-09-10","duration":1}
                        """.formatted(empId, typeId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reqId = mapper.readTree(reqJson).get("id").asText();

    mockMvc
        .perform(
            post("/api/hr/leave/requests/" + reqId + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approved":false,"decidedBy":"mgr"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
  }

  @Test
  void getLeaveBalances_returns200() throws Exception {
    String empId = createEmployee("LC-005");
    createLeaveType("VL5", "PAID", 1.25, false);

    mockMvc
        .perform(get("/api/hr/leave/balances").param("employeeId", empId))
        .andExpect(status().isOk());
  }
}
