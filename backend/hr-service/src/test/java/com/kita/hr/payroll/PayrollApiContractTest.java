package com.kita.hr.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.support.AbstractHrIT;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T019: contract test for the payroll run endpoints (MVP: salaried run reconciles + guards). */
class PayrollApiContractTest extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private static final String PERIOD =
      "{\"period\":{\"frequency\":\"MONTHLY\",\"startDate\":\"2026-01-01\","
          + "\"endDate\":\"2026-01-31\",\"payDate\":\"2026-01-31\"}}";

  private void seedEmployee() throws Exception {
    String body =
        """
        {"employeeNo":"E-1","firstName":"A","lastName":"B",
         "employmentType":"REGULAR","dateHired":"2025-01-01"}
        """;
    String json =
        mockMvc
            .perform(
                post("/api/hr/employees").contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(json).get("id").asText();
    mockMvc.perform(
        post("/api/hr/employees/" + id + "/compensation")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"effectiveDate\":\"2026-01-01\",\"basicPay\":30000.00,\"payFrequency\":\"MONTHLY\"}"));
  }

  private String createRun() throws Exception {
    String json =
        mockMvc
            .perform(
                post("/api/hr/payroll/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PERIOD))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = mapper.readTree(json);
    return node.get("id").asText();
  }

  @Test
  void fullRunComputesFinalizesAndRegisterReconciles() throws Exception {
    seedEmployee();
    String runId = createRun();

    mockMvc
        .perform(post("/api/hr/payroll/runs/" + runId + "/compute"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payslipCount").value(1))
        .andExpect(jsonPath("$.flagged.length()").value(0));

    mockMvc
        .perform(post("/api/hr/payroll/runs/" + runId + "/finalize"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINALIZED"));

    String json =
        mockMvc
            .perform(get("/api/hr/payroll/runs/" + runId + "/register"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employeeCount").value(1))
            .andExpect(jsonPath("$.totalGross").value(30000.00))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // SC-002: the register reconciles to the cent — net = gross − deductions, and each register
    // total is the sum of its payslips. Asserted as an invariant rather than a hard-coded net so
    // the contract does not depend on the statutory rates in effect.
    JsonNode reg = mapper.readTree(json);
    BigDecimal gross = reg.get("totalGross").decimalValue();
    BigDecimal deductions = reg.get("totalDeductions").decimalValue();
    BigDecimal net = reg.get("totalNet").decimalValue();
    assertThat(net).isEqualByComparingTo(gross.subtract(deductions));

    BigDecimal slipGross = BigDecimal.ZERO;
    BigDecimal slipNet = BigDecimal.ZERO;
    for (JsonNode slip : reg.get("payslips")) {
      slipGross = slipGross.add(slip.get("gross").decimalValue());
      slipNet = slipNet.add(slip.get("netPay").decimalValue());
    }
    assertThat(slipGross).isEqualByComparingTo(gross);
    assertThat(slipNet).isEqualByComparingTo(net);
  }

  @Test
  void duplicateRunForSamePeriodIsRejected() throws Exception {
    seedEmployee();
    createRun();
    mockMvc
        .perform(post("/api/hr/payroll/runs").contentType(MediaType.APPLICATION_JSON).content(PERIOD))
        .andExpect(status().isConflict());
  }

  @Test
  void finalizeBeforeComputeIsRejected() throws Exception {
    seedEmployee();
    String runId = createRun();
    mockMvc
        .perform(post("/api/hr/payroll/runs/" + runId + "/finalize"))
        .andExpect(status().isConflict());
  }

  @Test
  void reFinalizeIsRejected() throws Exception {
    seedEmployee();
    String runId = createRun();
    mockMvc.perform(post("/api/hr/payroll/runs/" + runId + "/compute")).andExpect(status().isOk());
    mockMvc.perform(post("/api/hr/payroll/runs/" + runId + "/finalize")).andExpect(status().isOk());
    mockMvc
        .perform(post("/api/hr/payroll/runs/" + runId + "/finalize"))
        .andExpect(status().isConflict());
  }
}
