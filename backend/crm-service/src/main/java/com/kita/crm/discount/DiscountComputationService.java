package com.kita.crm.discount;

import com.kita.crm.common.Money;
import com.kita.crm.customer.Customer;
import com.kita.crm.customer.CustomerRepository;
import com.kita.crm.entitlement.Entitlement;
import com.kita.crm.entitlement.EntitlementKind;
import com.kita.crm.entitlement.EntitlementRepository;
import com.kita.crm.loyalty.LoyaltyTier;
import com.kita.crm.loyalty.LoyaltyTierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the applicable tier list for a customer + sale date and folds it through {@link
 * CascadingEngine}, then combines the statutory and promotional paths per the active {@link
 * StackingPolicy}.
 *
 * <p>An unknown/absent customer is a walk-in: no entitlements, no loyalty, promotional tiers only —
 * never an error (FR-004).
 */
@Service
public class DiscountComputationService {

  /** Raised when a statutory discount was withheld for lack of a supporting ID (FR-014). */
  public static final String FLAG_ENTITLEMENT_WITHHELD = "ENTITLEMENT_WITHHELD";

  private final DiscountRuleRepository rules;
  private final StackingPolicyRepository policies;
  private final CustomerRepository customers;
  private final EntitlementRepository entitlements;
  private final LoyaltyTierRepository loyaltyTiers;

  public DiscountComputationService(
      DiscountRuleRepository rules,
      StackingPolicyRepository policies,
      CustomerRepository customers,
      EntitlementRepository entitlements,
      LoyaltyTierRepository loyaltyTiers) {
    this.rules = rules;
    this.policies = policies;
    this.customers = customers;
    this.entitlements = entitlements;
    this.loyaltyTiers = loyaltyTiers;
  }

  /** One line of the sale being priced. */
  public record LineItem(String itemRef, BigDecimal quantity, BigDecimal unitPrice) {}

  public record Computation(
      BigDecimal baseTotal,
      BigDecimal finalPrice,
      StackingMode stackingMode,
      List<CascadingEngine.BreakdownLine> breakdown,
      List<String> flags) {}

  @Transactional(readOnly = true)
  public Computation compute(UUID customerId, LocalDate saleDate, List<LineItem> lineItems) {
    BigDecimal baseTotal = baseTotal(lineItems);
    StackingMode mode = activeMode();
    List<String> flags = new ArrayList<>();

    Customer customer =
        customerId == null ? null : customers.findById(customerId).orElse(null); // unknown = walk-in

    List<DiscountRule> effective = effectiveRules(saleDate);
    List<CascadingEngine.Tier> promotional = promotionalTiers(effective, customer);
    List<CascadingEngine.Tier> statutory = statutoryTiers(effective, customer, saleDate, flags);

    CascadingEngine.Result result = combine(baseTotal, mode, promotional, statutory);
    flags.addAll(result.flags());
    return new Computation(baseTotal, result.finalPrice(), mode, result.breakdown(), flags);
  }

  /** Apply the two tier sets per the policy mode. */
  private CascadingEngine.Result combine(
      BigDecimal base,
      StackingMode mode,
      List<CascadingEngine.Tier> promotional,
      List<CascadingEngine.Tier> statutory) {
    return switch (mode) {
      case STATUTORY_ONLY -> CascadingEngine.apply(base, statutory);
      case STATUTORY_THEN_PROMO -> CascadingEngine.apply(base, concat(statutory, promotional));
      case PROMO_THEN_STATUTORY -> CascadingEngine.apply(base, concat(promotional, statutory));
      case MOST_FAVORABLE -> mostFavorable(base, promotional, statutory);
    };
  }

  /**
   * Compute both paths independently and return whichever leaves the customer paying less — never
   * both stacked (FR-013).
   */
  private CascadingEngine.Result mostFavorable(
      BigDecimal base, List<CascadingEngine.Tier> promotional, List<CascadingEngine.Tier> statutory) {
    CascadingEngine.Result promo = CascadingEngine.apply(base, promotional);
    CascadingEngine.Result stat = CascadingEngine.apply(base, statutory);
    if (stat.breakdown().isEmpty()) {
      return promo;
    }
    if (promo.breakdown().isEmpty()) {
      return stat;
    }
    return stat.finalPrice().compareTo(promo.finalPrice()) <= 0 ? stat : promo;
  }

  /**
   * Re-prioritise a concatenated cascade so the first group genuinely runs first, regardless of the
   * priorities the two groups happen to carry.
   */
  private List<CascadingEngine.Tier> concat(
      List<CascadingEngine.Tier> first, List<CascadingEngine.Tier> second) {
    List<CascadingEngine.Tier> out = new ArrayList<>();
    int p = 0;
    for (CascadingEngine.Tier t : ordered(first)) {
      out.add(reprioritised(t, p++));
    }
    for (CascadingEngine.Tier t : ordered(second)) {
      out.add(reprioritised(t, p++));
    }
    return out;
  }

  private List<CascadingEngine.Tier> ordered(List<CascadingEngine.Tier> tiers) {
    return tiers.stream()
        .sorted(
            Comparator.comparingInt(CascadingEngine.Tier::priority)
                .thenComparing(CascadingEngine.Tier::code))
        .toList();
  }

  private CascadingEngine.Tier reprioritised(CascadingEngine.Tier t, int priority) {
    return new CascadingEngine.Tier(t.code(), t.origin(), t.kind(), t.value(), priority);
  }

  private List<CascadingEngine.Tier> promotionalTiers(
      List<DiscountRule> effective, Customer customer) {
    List<CascadingEngine.Tier> tiers = new ArrayList<>();
    for (DiscountRule r : effective) {
      if (r.getOrigin() == DiscountOrigin.PROMOTIONAL) {
        tiers.add(r.toTier());
      }
    }
    loyaltyTier(effective, customer).ifPresent(tiers::add);
    return tiers;
  }

  /**
   * The customer's evaluated loyalty tier, as a cascade tier (FR-010). Only honoured when the tier's
   * rule is effective for the sale date, so an expired loyalty rule stops applying on its own.
   */
  private Optional<CascadingEngine.Tier> loyaltyTier(List<DiscountRule> effective, Customer customer) {
    if (customer == null || customer.getLoyaltyTierId() == null) {
      return Optional.empty();
    }
    Optional<LoyaltyTier> tier = loyaltyTiers.findById(customer.getLoyaltyTierId());
    if (tier.isEmpty()) {
      return Optional.empty();
    }
    UUID ruleId = tier.get().getDiscountRuleId();
    return effective.stream()
        .filter(r -> r.getId().equals(ruleId))
        .findFirst()
        .map(DiscountRule::toTier);
  }

  /**
   * Statutory tiers for the entitlements this customer can actually claim on the sale date. A rule
   * only fires for its own entitlement kind, so a senior rule never applies to a PWD-only customer.
   * An entitlement without its supporting ID is withheld and flagged rather than honoured (FR-014).
   */
  private List<CascadingEngine.Tier> statutoryTiers(
      List<DiscountRule> effective, Customer customer, LocalDate saleDate, List<String> flags) {
    if (customer == null) {
      return List.of(); // walk-in: no entitlements to honour
    }
    List<Entitlement> valid =
        entitlements.findByCustomerId(customer.getId()).stream()
            .filter(e -> e.isValidOn(saleDate))
            .toList();
    if (valid.isEmpty()) {
      return List.of();
    }

    Set<EntitlementKind> honoured =
        valid.stream()
            .filter(Entitlement::hasSupportingId)
            .map(Entitlement::getKind)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(EntitlementKind.class)));
    Set<EntitlementKind> withheld =
        valid.stream()
            .filter(e -> !e.hasSupportingId())
            .map(Entitlement::getKind)
            .filter(k -> !honoured.contains(k))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(EntitlementKind.class)));
    if (!withheld.isEmpty()) {
      flags.add(FLAG_ENTITLEMENT_WITHHELD);
    }
    if (honoured.isEmpty()) {
      return List.of();
    }

    List<CascadingEngine.Tier> tiers = new ArrayList<>();
    for (DiscountRule r : effective) {
      if (r.getOrigin() != DiscountOrigin.STATUTORY
          || r.getEntitlementKind() == null
          || !honoured.contains(r.getEntitlementKind())) {
        continue;
      }
      // The VAT-exemption step precedes the statutory percentage it belongs to.
      r.vatExemptionTier().ifPresent(tiers::add);
      tiers.add(r.toTier());
    }
    return tiers;
  }

  /** The latest version of each rule code effective on or before {@code saleDate}. */
  @Transactional(readOnly = true)
  public List<DiscountRule> effectiveRules(LocalDate saleDate) {
    Map<String, DiscountRule> latest = new LinkedHashMap<>();
    for (DiscountRule r : rules.findByEffectiveDateLessThanEqual(saleDate)) {
      DiscountRule cur = latest.get(r.getCode());
      if (cur == null || r.effectiveDate().isAfter(cur.effectiveDate())) {
        latest.put(r.getCode(), r);
      }
    }
    List<DiscountRule> out = new ArrayList<>(latest.values());
    out.sort(Comparator.comparingInt(DiscountRule::getPriority).thenComparing(DiscountRule::getCode));
    return out;
  }

  @Transactional(readOnly = true)
  public StackingMode activeMode() {
    return policies.findAllByOrderByUpdatedAtDesc().stream()
        .findFirst()
        .map(StackingPolicy::getMode)
        .orElse(StackingMode.MOST_FAVORABLE);
  }

  private BigDecimal baseTotal(List<LineItem> lineItems) {
    List<BigDecimal> amounts = new ArrayList<>();
    for (LineItem l : lineItems) {
      amounts.add(l.quantity().multiply(l.unitPrice()));
    }
    return Money.sum(amounts);
  }
}
