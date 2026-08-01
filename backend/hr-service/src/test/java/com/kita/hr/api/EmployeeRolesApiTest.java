package com.kita.hr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.employee.IdentityChange;
import com.kita.hr.employee.IdentityChangeRepository;
import com.kita.hr.support.AbstractOwnerAuthorizedTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 017 US2 — granting the roles the personnel record now holds (FR-014/FR-015).
 *
 * <p>Two properties carry the weight here. Assignment is an <b>idempotent full-set replace</b>, so a
 * retried request cannot double-grant or half-apply; and every grant or revocation is <b>attributable</b>
 * — a privilege change with no named administrator behind it is exactly what SC-009 forbids.
 */
class EmployeeRolesApiTest extends AbstractOwnerAuthorizedTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired private IdentityChangeRepository changes;

  private UUID createEmployee(String employeeNo) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/hr/employees")
                    .headers(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"employeeNo":"%s","firstName":"R","lastName":"S",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """
                            .formatted(employeeNo)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(mapper.readTree(body).get("id").asText());
  }

  private org.springframework.test.web.servlet.ResultActions setRoles(UUID id, String rolesJson)
      throws Exception {
    return mockMvc.perform(
        put("/api/hr/employees/{id}/roles", id)
            .headers(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":%s}".formatted(rolesJson)));
  }

  @Test
  void grantedRolesAreReturnedOnTheEmployeeRead() throws Exception {
    UUID id = createEmployee("ROLE-1");

    setRoles(id, "[\"SALES\",\"CASHIER\"]").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/employees/{id}", id).headers(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("SALES", "CASHIER")));
  }

  @Test
  void assignmentIsAnIdempotentReplaceSoARetryCannotDoubleGrant() throws Exception {
    UUID id = createEmployee("ROLE-2");

    setRoles(id, "[\"SALES\"]").andExpect(status().is2xxSuccessful());
    setRoles(id, "[\"SALES\"]").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/employees/{id}", id).headers(owner()))
        .andExpect(jsonPath("$.roles.length()").value(1));
  }

  @Test
  void replacingTheSetRevokesWhatIsNoLongerListed() throws Exception {
    UUID id = createEmployee("ROLE-3");
    setRoles(id, "[\"SALES\",\"CASHIER\"]").andExpect(status().is2xxSuccessful());

    setRoles(id, "[\"CASHIER\"]").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/employees/{id}", id).headers(owner()))
        .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("CASHIER")));
  }

  @Test
  void anUnrecognizedTokenIsStoredNotRejected() throws Exception {
    UUID id = createEmployee("ROLE-4");

    // hr must not validate against another service's enum: a service has to be able to add a role
    // without an hr redeploy. Downstream, an unknown token simply grants nothing (FR-004).
    setRoles(id, "[\"SOME_FUTURE_ROLE\"]").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/employees/{id}", id).headers(owner()))
        .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("SOME_FUTURE_ROLE")));
  }

  @Test
  void everyGrantAndRevocationIsAttributableToAnAdministrator() throws Exception {
    UUID id = createEmployee("ROLE-5");
    setRoles(id, "[\"SALES\"]").andExpect(status().is2xxSuccessful());
    setRoles(id, "[]").andExpect(status().is2xxSuccessful());

    List<IdentityChange> audit = changes.findByEmployeeIdOrderByChangedAtDesc(id);
    assertThat(audit).extracting(IdentityChange::getAction).contains("ROLE_GRANTED", "ROLE_REVOKED");
    assertThat(audit).allSatisfy(c -> assertThat(c.getChangedBy()).isNotBlank());
    assertThat(audit)
        .filteredOn(c -> c.getAction().startsWith("ROLE_"))
        .allSatisfy(c -> assertThat(c.getRole()).isNotBlank());
  }

  @Test
  void aCallerWithoutOwnerCannotGrantRoles() throws Exception {
    UUID id = createEmployee("ROLE-6");

    mockMvc
        .perform(
            put("/api/hr/employees/{id}/roles", id)
                .headers(nonOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"OWNER\"]}"))
        .andExpect(status().isForbidden());
  }
}
