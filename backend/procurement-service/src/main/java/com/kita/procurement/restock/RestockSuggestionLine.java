package com.kita.procurement.restock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One item to replenish. {@code onHand}/{@code targetLevel} are recorded so the sizing is auditable. */
@Entity
@Table(name = "restock_suggestion_line")
public class RestockSuggestionLine {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "suggestion_id", nullable = false)
  private UUID suggestionId;

  @Column(name = "item_ref", nullable = false)
  private String itemRef;

  @Column(name = "suggested_qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal suggestedQty;

  @Column(name = "on_hand", nullable = false, precision = 19, scale = 4)
  private BigDecimal onHand;

  @Column(name = "target_level", nullable = false, precision = 19, scale = 4)
  private BigDecimal targetLevel;

  @Column private String reason;

  protected RestockSuggestionLine() {}

  public RestockSuggestionLine(
      UUID suggestionId,
      String itemRef,
      BigDecimal suggestedQty,
      BigDecimal onHand,
      BigDecimal targetLevel,
      String reason) {
    this.suggestionId = suggestionId;
    this.itemRef = itemRef;
    this.suggestedQty = suggestedQty;
    this.onHand = onHand;
    this.targetLevel = targetLevel;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSuggestionId() {
    return suggestionId;
  }

  public String getItemRef() {
    return itemRef;
  }

  public BigDecimal getSuggestedQty() {
    return suggestedQty;
  }

  public BigDecimal getOnHand() {
    return onHand;
  }

  public BigDecimal getTargetLevel() {
    return targetLevel;
  }

  public String getReason() {
    return reason;
  }
}
