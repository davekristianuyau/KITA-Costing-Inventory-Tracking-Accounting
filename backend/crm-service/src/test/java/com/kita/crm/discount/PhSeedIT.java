package com.kita.crm.discount;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerService;
import com.kita.crm.customer.CustomerType;
import com.kita.crm.entitlement.Entitlement;
import com.kita.crm.entitlement.EntitlementKind;
import com.kita.crm.entitlement.EntitlementService;
import com.kita.crm.support.AbstractCrmIT;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the shipped PH seed itself (FR-012), not a hand-copied imitation of it.
 *
 * <p>AbstractCrmIT truncates discount_rule so tests cannot leak rules into one another, which also
 * removes the V5 seed. This re-applies V5's INSERT statements straight from the migration file, so a
 * wrong rate or VAT treatment in the seed fails here.
 */
class PhSeedIT extends AbstractCrmIT {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private CustomerService customers;
  @Autowired private EntitlementService entitlements;
  @Autowired private DiscountComputationService computation;

  /** INSERT statements only — V5's ALTER TABLEs have already been applied by Flyway. */
  private static final Pattern INSERTS =
      Pattern.compile("(?is)(INSERT\\s+INTO\\s+discount_rule.*?;)");

  @BeforeEach
  void reapplySeed() throws IOException {
    String sql;
    try (InputStream in = new ClassPathResource("db/migration/V5__seed_ph_discounts.sql").getInputStream()) {
      sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    Matcher m = INSERTS.matcher(sql);
    int applied = 0;
    while (m.find()) {
      jdbc.execute(m.group(1));
      applied++;
    }
    assertThat(applied).as("seed INSERT statements found in V5").isPositive();
  }

  private UUID seniorCustomer(String code, String supportingId) {
    UUID id =
        customers
            .create(new CreateCustomerRequest(code, CustomerType.INDIVIDUAL, "Lolo", null, null, null), "crm")
            .getId();
    entitlements.add(
        id,
        new Entitlement(id, EntitlementKind.SENIOR, supportingId, LocalDate.of(2024, 1, 1), null),
        "crm");
    return id;
  }

  private DiscountComputationService.Computation compute(UUID id) {
    return computation.compute(
        id,
        LocalDate.of(2026, 3, 1),
        List.of(new DiscountComputationService.LineItem("SKU", BigDecimal.ONE, new BigDecimal("1120.00"))));
  }

  @Test
  void seededRulesAreEffectiveAndCarryTheirVatTreatment() {
    List<DiscountRule> effective = computation.effectiveRules(LocalDate.of(2026, 3, 1));

    assertThat(effective).extracting(DiscountRule::getCode).contains("PH_SENIOR", "PH_PWD");
    DiscountRule senior =
        effective.stream().filter(r -> r.getCode().equals("PH_SENIOR")).findFirst().orElseThrow();
    assertThat(senior.getOrigin()).isEqualTo(DiscountOrigin.STATUTORY);
    assertThat(senior.getValue()).isEqualByComparingTo("0.20");
    assertThat(senior.getVatTreatment()).isEqualTo(VatTreatment.VAT_EXEMPT);
    assertThat(senior.getVatRate()).isEqualByComparingTo("0.12");
    assertThat(senior.getEntitlementKind()).isEqualTo(EntitlementKind.SENIOR);
  }

  /** The seeded senior rule prices a VAT-inclusive 1120 at 800 for an entitled customer. */
  @Test
  void seededSeniorRuleComputesTheExpectedPrice() {
    DiscountComputationService.Computation c = compute(seniorCustomer("PH-1", "SC-123"));

    assertThat(c.finalPrice()).isEqualByComparingTo("800.00");
    assertThat(c.breakdown())
        .extracting(CascadingEngine.BreakdownLine::tierCode)
        .containsExactly("PH_SENIOR_VAT_EXEMPT", "PH_SENIOR");
  }

  /** The PWD rule must not fire for a senior-only customer, even though both are seeded. */
  @Test
  void seededPwdRuleDoesNotFireForASeniorOnlyCustomer() {
    DiscountComputationService.Computation c = compute(seniorCustomer("PH-2", "SC-123"));

    assertThat(c.breakdown())
        .extracting(CascadingEngine.BreakdownLine::tierCode)
        .doesNotContain("PH_PWD", "PH_PWD_VAT_EXEMPT");
  }

  @Test
  void seededRuleIsWithheldWithoutASupportingId() {
    DiscountComputationService.Computation c = compute(seniorCustomer("PH-3", null));

    assertThat(c.finalPrice()).isEqualByComparingTo("1120.00");
    assertThat(c.flags()).contains(DiscountComputationService.FLAG_ENTITLEMENT_WITHHELD);
  }
}
