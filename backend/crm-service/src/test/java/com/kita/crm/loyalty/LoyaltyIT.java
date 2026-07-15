package com.kita.crm.loyalty;

import static org.assertj.core.api.Assertions.assertThat;

import com.kita.crm.customer.CreateCustomerRequest;
import com.kita.crm.customer.CustomerRepository;
import com.kita.crm.customer.CustomerService;
import com.kita.crm.customer.CustomerType;
import com.kita.crm.discount.DiscountComputationKind;
import com.kita.crm.discount.DiscountComputationService;
import com.kita.crm.discount.DiscountOrigin;
import com.kita.crm.discount.DiscountRule;
import com.kita.crm.discount.DiscountRuleRepository;
import com.kita.crm.discount.VatTreatment;
import com.kita.crm.support.AbstractCrmIT;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** T026 (SC-004): tier eligibility from qualifying activity, re-evaluation, and cascade impact. */
class LoyaltyIT extends AbstractCrmIT {

  @Autowired private CustomerService customers;
  @Autowired private CustomerRepository customerRepo;
  @Autowired private LoyaltyService loyalty;
  @Autowired private DiscountRuleRepository rules;
  @Autowired private DiscountComputationService computation;

  private static final LocalDate SALE = LocalDate.of(2026, 3, 1);

  private UUID customer(String code) {
    return customers
        .create(new CreateCustomerRequest(code, CustomerType.INDIVIDUAL, "Ana", null, null, null), "crm")
        .getId();
  }

  private UUID rule(String code, String value) {
    return rules
        .save(
            new DiscountRule(
                code, DiscountOrigin.LOYALTY, DiscountComputationKind.PERCENT,
                new BigDecimal(value), VatTreatment.NONE, 10, LocalDate.of(2026, 1, 1)))
        .getId();
  }

  private UUID tier(String code, Integer minCount, String minValue, UUID ruleId) {
    return loyalty
        .createTier(
            new LoyaltyTier(
                code, code + " tier", minCount,
                minValue == null ? null : new BigDecimal(minValue), 365, ruleId),
            "crm")
        .getId();
  }

  @Test
  void qualifyingActivityAssignsTheTierAndNonQualifyingDoesNot() {
    UUID silver = tier("SILVER", 5, "10000", rule("LOY_SILVER", "0.05"));
    UUID qualifying = customer("LY-1");
    UUID notQualifying = customer("LY-2");

    Optional<LoyaltyTier> assigned =
        loyalty.evaluate(qualifying, new LoyaltyService.Activity(6, new BigDecimal("12000")), "crm");
    assertThat(assigned).isPresent();
    assertThat(assigned.get().getId()).isEqualTo(silver);
    assertThat(customerRepo.findById(qualifying).orElseThrow().getLoyaltyTierId()).isEqualTo(silver);

    // Meets the value threshold but not the count — every specified criterion must hold.
    Optional<LoyaltyTier> none =
        loyalty.evaluate(notQualifying, new LoyaltyService.Activity(2, new BigDecimal("12000")), "crm");
    assertThat(none).isEmpty();
    assertThat(customerRepo.findById(notQualifying).orElseThrow().getLoyaltyTierId()).isNull();
  }

  @Test
  void theMostDemandingQualifyingTierWins() {
    rule("LOY_S", "0.05");
    UUID silverRule = rules.findAll().get(0).getId();
    tier("SILVER", 5, "10000", silverRule);
    UUID goldTier = tier("GOLD", 10, "50000", rule("LOY_G", "0.10"));

    UUID id = customer("LY-3");
    Optional<LoyaltyTier> assigned =
        loyalty.evaluate(id, new LoyaltyService.Activity(12, new BigDecimal("60000")), "crm");

    assertThat(assigned).isPresent();
    assertThat(assigned.get().getId()).isEqualTo(goldTier); // qualifies for both; gold is higher
  }

  /** FR-011: falling below the threshold re-evaluates the customer down. */
  @Test
  void reEvaluationClearsATierWhenActivityNoLongerQualifies() {
    tier("SILVER", 5, "10000", rule("LOY_SILVER", "0.05"));
    UUID id = customer("LY-4");

    loyalty.evaluate(id, new LoyaltyService.Activity(6, new BigDecimal("12000")), "crm");
    assertThat(customerRepo.findById(id).orElseThrow().getLoyaltyTierId()).isNotNull();

    loyalty.evaluate(id, new LoyaltyService.Activity(1, new BigDecimal("100")), "crm");
    assertThat(customerRepo.findById(id).orElseThrow().getLoyaltyTierId()).isNull();
  }

  /** SC-004: the assigned tier actually contributes its discount to the cascade. */
  @Test
  void assignedTierContributesItsDiscountToTheComputation() {
    tier("SILVER", 5, "10000", rule("LOY_SILVER", "0.05"));
    UUID id = customer("LY-5");
    loyalty.evaluate(id, new LoyaltyService.Activity(6, new BigDecimal("12000")), "crm");

    DiscountComputationService.Computation c =
        computation.compute(
            id, SALE, List.of(new DiscountComputationService.LineItem("SKU", BigDecimal.ONE, new BigDecimal("1000.00"))));

    assertThat(c.finalPrice()).isEqualByComparingTo("950.00"); // 1000 - 5%
    assertThat(c.breakdown()).hasSize(1);
    assertThat(c.breakdown().get(0).origin()).isEqualTo(DiscountOrigin.LOYALTY);
    assertThat(c.breakdown().get(0).amountRemoved()).isEqualByComparingTo("50.00");
  }

  @Test
  void customerWithoutATierGetsNoLoyaltyDiscount() {
    tier("SILVER", 5, "10000", rule("LOY_SILVER", "0.05"));
    UUID id = customer("LY-6");

    DiscountComputationService.Computation c =
        computation.compute(
            id, SALE, List.of(new DiscountComputationService.LineItem("SKU", BigDecimal.ONE, new BigDecimal("1000.00"))));

    assertThat(c.finalPrice()).isEqualByComparingTo("1000.00");
    assertThat(c.breakdown()).isEmpty();
  }

  /** A loyalty rule not yet effective for the sale date must not apply. */
  @Test
  void loyaltyRuleNotEffectiveForTheSaleDateDoesNotApply() {
    UUID futureRule =
        rules
            .save(
                new DiscountRule(
                    "LOY_FUTURE", DiscountOrigin.LOYALTY, DiscountComputationKind.PERCENT,
                    new BigDecimal("0.05"), VatTreatment.NONE, 10, LocalDate.of(2027, 1, 1)))
            .getId();
    tier("FUTURE", 1, null, futureRule);
    UUID id = customer("LY-7");
    loyalty.evaluate(id, new LoyaltyService.Activity(5, new BigDecimal("5000")), "crm");

    DiscountComputationService.Computation c =
        computation.compute(
            id, SALE, List.of(new DiscountComputationService.LineItem("SKU", BigDecimal.ONE, new BigDecimal("1000.00"))));

    assertThat(c.finalPrice()).isEqualByComparingTo("1000.00");
    assertThat(c.breakdown()).isEmpty();
  }
}
