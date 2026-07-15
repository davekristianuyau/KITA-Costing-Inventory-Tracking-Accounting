package com.kita.hr.remittance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.support.AbstractHrIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T052: contract tests for /payroll/runs/{id}/remittances and /payslips (EMPLOYEE_SELF scope). */
class RemittanceApiContractTest extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private String createEmployee(String no) throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeNo":"%s","firstName":"Rem","lastName":"It",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """
                            .formatted(no)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(json).get("id").asText();
    mockMvc
        .perform(
            post("/api/hr/employees/" + id + "/compensation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"effectiveDate\":\"2026-01-01\",\"basicPay\":30000.00,"
                        + "\"payFrequency\":\"MONTHLY\"}"))
        .andExpect(status().isCreated());
    return id;
  }

  private String finalizedRun() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/payroll/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"period\":{\"frequency\":\"MONTHLY\",\"startDate\":\"2026-01-01\","
                            + "\"endDate\":\"2026-01-31\",\"payDate\":\"2026-01-31\"}}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String runId = mapper.readTree(json).get("id").asText();
    mockMvc.perform(post("/api/hr/payroll/runs/" + runId + "/compute")).andExpect(status().isOk());
    mockMvc.perform(post("/api/hr/payroll/runs/" + runId + "/finalize")).andExpect(status().isOk());
    return runId;
  }

  @Test
  void remittanceSummaryGroupsByAgency() throws Exception {
    createEmployee("RM-1");
    String runId = finalizedRun();

    mockMvc
        .perform(get("/api/hr/payroll/runs/" + runId + "/remittances"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value(runId))
        .andExpect(jsonPath("$.agencies.length()").value(4))
        .andExpect(jsonPath("$.agencies[?(@.agency=='SSS')].employeeTotal").value(1350.00))
        .andExpect(jsonPath("$.agencies[?(@.agency=='SSS')].employerTotal").value(2850.00))
        .andExpect(jsonPath("$.agencies[?(@.agency=='BIR')].employerTotal").value(0.00));
  }

  @Test
  void remittanceForUnknownRunReturns404() throws Exception {
    mockMvc
        .perform(get("/api/hr/payroll/runs/00000000-0000-0000-0000-000000000000/remittances"))
        .andExpect(status().isNotFound());
  }

  @Test
  void employeeSelfCanReadOwnPayslips() throws Exception {
    String id = createEmployee("RM-2");
    finalizedRun();

    mockMvc
        .perform(
            get("/api/hr/payslips")
                .param("employeeId", id)
                .header("X-Kita-Roles", "EMPLOYEE_SELF")
                .header("X-Kita-Employee-Id", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].employeeId").value(id));
  }

  @Test
  void employeeSelfDefaultsToOwnPayslipsWhenNoEmployeeIdGiven() throws Exception {
    String id = createEmployee("RM-3");
    createEmployee("RM-4");
    finalizedRun();

    mockMvc
        .perform(
            get("/api/hr/payslips")
                .header("X-Kita-Roles", "EMPLOYEE_SELF")
                .header("X-Kita-Employee-Id", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].employeeId").value(id));
  }

  @Test
  void employeeSelfCannotReadAnotherEmployeesPayslips() throws Exception {
    String mine = createEmployee("RM-5");
    String theirs = createEmployee("RM-6");
    finalizedRun();

    mockMvc
        .perform(
            get("/api/hr/payslips")
                .param("employeeId", theirs)
                .header("X-Kita-Roles", "EMPLOYEE_SELF")
                .header("X-Kita-Employee-Id", mine))
        .andExpect(status().isForbidden());
  }

  @Test
  void employeeSelfCannotReadRemittances() throws Exception {
    String id = createEmployee("RM-7");
    String runId = finalizedRun();

    mockMvc
        .perform(
            get("/api/hr/payroll/runs/" + runId + "/remittances")
                .header("X-Kita-Roles", "EMPLOYEE_SELF")
                .header("X-Kita-Employee-Id", id))
        .andExpect(status().isForbidden());
  }
}
