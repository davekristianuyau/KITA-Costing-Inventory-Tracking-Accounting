package com.kita.hr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kita.hr.employee.IdentityChange;
import com.kita.hr.support.AbstractOwnerAuthorizedTest;
import com.kita.hr.employee.IdentityChangeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 017 US2 — administering the account↔employee link. The rules that matter here are the ones that stop
 * an identity from quietly moving to the wrong person: the link is strictly one-to-one (refused in
 * <em>both</em> directions), re-linking is an explicit unlink-then-link rather than a silent overwrite,
 * and every change names the administrator who made it.
 */
class AccountLinkApiTest extends AbstractOwnerAuthorizedTest {

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
                        {"employeeNo":"%s","firstName":"A","lastName":"B",
                         "employmentType":"REGULAR","dateHired":"2025-01-01"}
                        """
                            .formatted(employeeNo)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(mapper.readTree(body).get("id").asText());
  }

  private org.springframework.test.web.servlet.ResultActions link(UUID id, String account)
      throws Exception {
    return mockMvc.perform(
        put("/api/hr/employees/{id}/account", id)
            .headers(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountUsername\":\"%s\"}".formatted(account)));
  }

  @Test
  void linkThenTheEmployeeResolvesByThatAccount() throws Exception {
    UUID id = createEmployee("LINK-1");

    link(id, "alice").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/employees/by-account/{u}", "alice").headers(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()));
  }

  @Test
  void unlinkThenTheAccountNoLongerResolves() throws Exception {
    UUID id = createEmployee("LINK-2");
    link(id, "bob").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(delete("/api/hr/employees/{id}/account", id).headers(owner()))
        .andExpect(status().is2xxSuccessful());

    // 404 = "no employee linked", which the caller reports distinctly from a permission refusal.
    mockMvc
        .perform(get("/api/hr/employees/by-account/{u}", "bob").headers(owner()))
        .andExpect(status().isNotFound());
  }

  @Test
  void anAccountAlreadyLinkedToSomeoneElseIsRefused() throws Exception {
    UUID first = createEmployee("LINK-3");
    UUID second = createEmployee("LINK-4");
    link(first, "carol").andExpect(status().is2xxSuccessful());

    link(second, "carol").andExpect(status().isConflict());
  }

  @Test
  void anEmployeeWhoAlreadyHasADifferentAccountIsRefused() throws Exception {
    UUID id = createEmployee("LINK-5");
    link(id, "dave").andExpect(status().is2xxSuccessful());

    // Re-linking must be an explicit unlink-then-link, never a silent overwrite.
    link(id, "erin").andExpect(status().isConflict());
  }

  @Test
  void relinkingAfterAnExplicitUnlinkIsAllowed() throws Exception {
    UUID id = createEmployee("LINK-6");
    link(id, "frank").andExpect(status().is2xxSuccessful());
    mockMvc
        .perform(delete("/api/hr/employees/{id}/account", id).headers(owner()))
        .andExpect(status().is2xxSuccessful());

    link(id, "grace").andExpect(status().is2xxSuccessful());
  }

  @Test
  void everyLinkAndUnlinkRecordsWhoDidItAndWhen() throws Exception {
    UUID id = createEmployee("LINK-7");
    link(id, "heidi").andExpect(status().is2xxSuccessful());
    mockMvc
        .perform(delete("/api/hr/employees/{id}/account", id).headers(owner()))
        .andExpect(status().is2xxSuccessful());

    List<IdentityChange> audit = changes.findByEmployeeIdOrderByChangedAtDesc(id);
    assertThat(audit).extracting(IdentityChange::getAction).containsExactly("UNLINKED", "LINKED");
    assertThat(audit).allSatisfy(c -> assertThat(c.getChangedBy()).isNotBlank());
    assertThat(audit).allSatisfy(c -> assertThat(c.getChangedAt()).isNotNull());
  }

  @Test
  void aCallerWithoutOwnerCannotAdministerLinks() throws Exception {
    UUID id = createEmployee("LINK-8");

    mockMvc
        .perform(
            put("/api/hr/employees/{id}/account", id)
                .headers(nonOwner())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountUsername\":\"mallory\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/hr/employees/{id}/account", id).headers(nonOwner()))
        .andExpect(status().isForbidden());
  }

  @Test
  void accountLinksCanBeListedForAudit() throws Exception {
    UUID id = createEmployee("LINK-9");
    link(id, "ivan").andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/hr/account-links").headers(owner()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.accountUsername=='ivan')].employeeId").value(id.toString()));
  }
}
