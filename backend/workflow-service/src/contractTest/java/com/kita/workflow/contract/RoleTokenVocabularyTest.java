package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 017 — the canonical role-token vocabulary, asserted across every service that authorizes.
 *
 * <p>Roles live in the personnel record as **one flat set of opaque strings** and each service
 * recognizes the subset it knows (research Decision 6). Nothing at runtime forces the four services to
 * spell a shared token the same way, so a rename in one enum would silently stop granting — the failure
 * would look like "that user just can't do it any more". This test is the thing that catches it.
 *
 * <p>It deliberately compares <em>names</em>, not enum identity: these services share no code.
 */
class RoleTokenVocabularyTest {

  /** Recognized by every service: the highest-position administrator (FR-017). */
  private static final String OWNER = "OWNER";

  /** Tokens the personnel record may hold. Extend here when a service adds a role. */
  private static final Set<String> CANONICAL =
      new TreeSet<>(
          List.of(
              OWNER,
              // workflow (back-office actions)
              "SALES", "CASHIER", "SALES_MANAGER", "WAREHOUSE_STAFF", "WAREHOUSE_MANAGER",
              "PROCUREMENT_STAFF", "PROCUREMENT_APPROVER", "PRODUCTION", "CRM_ADMIN",
              // hr's own API
              "HR_ADMIN", "PAYROLL_OFFICER", "MANAGER", "EMPLOYEE_SELF",
              // procurement's own API
              "PROCUREMENT_ADMIN", "APPROVER", "RECEIVER"));

  private static Set<String> namesOf(Class<? extends Enum<?>> roleEnum) {
    return Arrays.stream(roleEnum.getEnumConstants())
        .map(Enum::name)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Test
  void everyServiceRecognizesOwner() {
    for (Class<? extends Enum<?>> roleEnum :
        List.of(
            com.kita.workflow.common.security.Role.class,
            com.kita.hr.common.security.Role.class,
            com.kita.crm.common.security.Role.class,
            com.kita.procurement.common.security.Role.class)) {
      assertThat(namesOf(roleEnum))
          .as("%s must recognize OWNER — it is the one token every service reads (FR-017)", roleEnum)
          .contains(OWNER);
    }
  }

  @Test
  void noServiceInventsATokenOutsideTheCanonicalVocabulary() {
    for (Class<? extends Enum<?>> roleEnum :
        List.of(
            com.kita.workflow.common.security.Role.class,
            com.kita.hr.common.security.Role.class,
            com.kita.crm.common.security.Role.class,
            com.kita.procurement.common.security.Role.class)) {
      assertThat(CANONICAL)
          .as(
              "%s holds a token the personnel record does not know, so it could never be granted."
                  + " Add it to CANONICAL (and to hr's admin vocabulary) or rename it.",
              roleEnum)
          .containsAll(namesOf(roleEnum));
    }
  }

  @Test
  void tokensSharedBetweenServicesAreSpelledIdentically() {
    // SALES and CRM_ADMIN exist in more than one service; a divergent spelling silently stops granting.
    assertThat(namesOf(com.kita.crm.common.security.Role.class)).contains("SALES", "CRM_ADMIN");
    assertThat(namesOf(com.kita.workflow.common.security.Role.class)).contains("SALES", "CRM_ADMIN");
  }
}
