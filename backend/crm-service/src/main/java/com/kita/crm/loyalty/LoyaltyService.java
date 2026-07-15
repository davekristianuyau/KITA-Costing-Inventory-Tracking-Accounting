package com.kita.crm.loyalty;

import com.kita.crm.common.AuditWriter;
import com.kita.crm.common.ConflictException;
import com.kita.crm.common.NotFoundException;
import com.kita.crm.customer.Customer;
import com.kita.crm.customer.CustomerRepository;
import com.kita.crm.discount.DiscountRuleRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates a customer's loyalty tier from qualifying activity and caches it on the customer
 * (FR-010/011). Purchase history is owned by operations-service, so the activity is supplied to this
 * service rather than queried here.
 */
@Service
public class LoyaltyService {

  private final LoyaltyTierRepository tiers;
  private final CustomerRepository customers;
  private final DiscountRuleRepository rules;
  private final AuditWriter audit;

  public LoyaltyService(
      LoyaltyTierRepository tiers,
      CustomerRepository customers,
      DiscountRuleRepository rules,
      AuditWriter audit) {
    this.tiers = tiers;
    this.customers = customers;
    this.rules = rules;
    this.audit = audit;
  }

  /** Qualifying activity for a customer over the tier's measurement window. */
  public record Activity(int purchaseCount, BigDecimal purchaseValue) {}

  @Transactional
  public LoyaltyTier createTier(LoyaltyTier tier, String actor) {
    if (tiers.findByCode(tier.getCode()).isPresent()) {
      throw new ConflictException("loyalty tier code already exists: " + tier.getCode());
    }
    if (!rules.existsById(tier.getDiscountRuleId())) {
      throw new NotFoundException("discount rule not found: " + tier.getDiscountRuleId());
    }
    LoyaltyTier saved = tiers.save(tier);
    audit.record(actor, "RULE_CHANGED", saved.getId().toString(), "loyalty tier=" + tier.getCode());
    return saved;
  }

  @Transactional(readOnly = true)
  public List<LoyaltyTier> listTiers() {
    return tiers.findAll();
  }

  /**
   * Re-evaluate and cache the customer's tier (FR-011). Assigns the most demanding tier the activity
   * qualifies for, or clears the tier when none does.
   */
  @Transactional
  public Optional<LoyaltyTier> evaluate(UUID customerId, Activity activity, String actor) {
    Customer customer =
        customers
            .findById(customerId)
            .orElseThrow(() -> new NotFoundException("customer not found: " + customerId));

    Optional<LoyaltyTier> best = bestQualifying(activity);
    UUID previous = customer.getLoyaltyTierId();
    customer.setLoyaltyTierId(best.map(LoyaltyTier::getId).orElse(null));
    customers.save(customer);

    if (!java.util.Objects.equals(previous, customer.getLoyaltyTierId())) {
      audit.record(
          actor,
          "CUSTOMER_CHANGED",
          customerId.toString(),
          "loyalty tier=" + best.map(LoyaltyTier::getCode).orElse("none"));
    }
    return best;
  }

  /** The most exclusive tier the activity satisfies: highest value threshold, then highest count. */
  private Optional<LoyaltyTier> bestQualifying(Activity activity) {
    return tiers.findAll().stream()
        .filter(t -> t.qualifies(activity.purchaseCount(), activity.purchaseValue()))
        .max(
            Comparator.comparing(
                    (LoyaltyTier t) ->
                        t.getMinPurchaseValue() == null ? BigDecimal.ZERO : t.getMinPurchaseValue())
                .thenComparingInt(
                    t -> t.getMinPurchaseCount() == null ? 0 : t.getMinPurchaseCount()));
  }
}
