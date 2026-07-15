package com.kita.hr.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.kita.hr.support.AbstractHrIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/** T013: persistence + compensation history retention and status lifecycle. */
class EmployeePersistenceIT extends AbstractHrIT {

  @Autowired private ObjectMapper mapper;

  private String createEmployee() throws Exception {
    String body =
        """
        {"employeeNo":"E-100","firstName":"Ben","lastName":"Reyes",
         "employmentType":"REGULAR","dateHired":"2026-01-01"}
        """;
    String json =
        mockMvc
            .perform(
                post("/api/hr/employees").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = mapper.readTree(json);
    return node.get("id").asText();
  }

  @Test
  void compensationHistoryRetainedAndOrdered() throws Exception {
    String id = createEmployee();
    addCompensation(id, "2026-01-01", "30000.00");
    addCompensation(id, "2026-07-01", "33000.00");

    mockMvc
        .perform(get("/api/hr/employees/" + id + "/compensation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].effectiveDate").value("2026-07-01")) // desc: latest first
        .andExpect(jsonPath("$[1].effectiveDate").value("2026-01-01"));
  }

  @Test
  void separationWithoutDateIsRejected() throws Exception {
    String id = createEmployee();
    mockMvc
        .perform(
            patch("/api/hr/employees/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SEPARATED\"}"))
        .andExpect(status().isConflict());
  }

  private void addCompensation(String id, String date, String pay) throws Exception {
    String body =
        "{\"effectiveDate\":\"%s\",\"basicPay\":%s,\"payFrequency\":\"MONTHLY\"}".formatted(date, pay);
    mockMvc
        .perform(
            post("/api/hr/employees/" + id + "/compensation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }
}
