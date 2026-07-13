package com.kita.hr.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kita.hr.support.AbstractHrIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T012: contract test for the employee endpoints. */
class EmployeeApiContractTest extends AbstractHrIT {

  private static final String VALID =
      """
      {"employeeNo":"E-001","firstName":"Ana","lastName":"Cruz",
       "employmentType":"REGULAR","dateHired":"2026-01-01"}
      """;

  @Test
  void createReturns201WithNumberAndActiveStatus() throws Exception {
    mockMvc
        .perform(post("/api/hr/employees").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.employeeNo").value("E-001"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void duplicateEmployeeNoReturns409() throws Exception {
    mockMvc
        .perform(post("/api/hr/employees").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isCreated());
    mockMvc
        .perform(post("/api/hr/employees").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isConflict());
  }

  @Test
  void missingRequiredFieldReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/hr/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ana\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUnknownReturns404() throws Exception {
    mockMvc
        .perform(get("/api/hr/employees/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }
}
