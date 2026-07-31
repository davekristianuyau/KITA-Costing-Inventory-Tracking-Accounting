package com.kita.hr.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.employee.Employee;
import com.kita.hr.employee.EmployeeRepository;
import com.kita.hr.employee.EmployeeRole;
import com.kita.hr.employee.EmployeeRoleRepository;
import com.kita.hr.support.AbstractHrIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 017 US1 — resolving an account to its employee is the call every governed action makes, so it must
 * answer status <em>and</em> roles in one round trip, and it must distinguish "this account has no
 * employee" (404) from "the employee exists but may not act" (200 with a non-ACTIVE status). Collapsing
 * those two is exactly the ambiguity SC-004 forbids.
 */
class EmployeeByAccountApiTest extends AbstractHrIT {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private EmployeeRepository employees;
  @Autowired private EmployeeRoleRepository roles;

  private UUID createEmployee(String employeeNo) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/hr/employees")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeNo":"%s","firstName":"Juan","lastName":"Dela Cruz",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """
                            .formatted(employeeNo)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(mapper.readTree(body).get("id").asText());
  }

  /** The link itself is administered in US2; here it is seeded so the read can be tested alone. */
  private void link(UUID employeeId, String account, String... grantedRoles) {
    Employee e = employees.findById(employeeId).orElseThrow();
    e.setAccountUsername(account);
    employees.save(e);
    for (String r : grantedRoles) {
      roles.save(new EmployeeRole(employeeId, r, "test"));
    }
  }

  @Test
  void returnsStatusAndRolesForALinkedAccount() throws Exception {
    UUID id = createEmployee("BYACC-1");
    link(id, "alice", "SALES", "CASHIER");

    mockMvc
        .perform(get("/api/hr/employees/by-account/{username}", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.accountUsername").value("alice"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("SALES", "CASHIER")));
  }

  @Test
  void unknownAccountIsNotFoundSoTheCallerCanSayNoEmployeeLinked() throws Exception {
    mockMvc
        .perform(get("/api/hr/employees/by-account/{username}", "nobody"))
        .andExpect(status().isNotFound());
  }

  @Test
  void aLinkedButInactiveEmployeeStillResolvesSoTheCallerCanNameTheStatus() throws Exception {
    UUID id = createEmployee("BYACC-2");
    link(id, "bob", "SALES");
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/hr/employees/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                // hr requires dateSeparated when separating someone — a 004 rule, not a 017 concern.
                .content("{\"status\":\"SEPARATED\",\"dateSeparated\":\"2025-06-30\"}"))
        .andExpect(status().isOk());

    // 200, NOT 404: "separated" must be distinguishable from "no employee linked" (FR-005/FR-006).
    mockMvc
        .perform(get("/api/hr/employees/by-account/{username}", "bob"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SEPARATED"));
  }

  @Test
  void anEmployeeWithNoRolesResolvesWithAnEmptyRoleList() throws Exception {
    UUID id = createEmployee("BYACC-3");
    link(id, "carol");

    mockMvc
        .perform(get("/api/hr/employees/by-account/{username}", "carol"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles").isEmpty());
  }
}
