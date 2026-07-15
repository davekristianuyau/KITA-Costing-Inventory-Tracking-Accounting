package com.kita.hr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A single itemized line on a payslip (earning, deduction, tax, or employer contribution). */
@Entity
@Table(name = "pay_component")
public class PayComponent {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "payslip_id", nullable = false)
  private UUID payslipId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PayComponentCategory category;

  @Column(nullable = false)
  private String code;

  @Column private String label;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column private String basis;

  protected PayComponent() {}

  public PayComponent(
      UUID payslipId,
      PayComponentCategory category,
      String code,
      String label,
      BigDecimal amount,
      String basis) {
    this.payslipId = payslipId;
    this.category = category;
    this.code = code;
    this.label = label;
    this.amount = amount;
    this.basis = basis;
  }

  public UUID getId() {
    return id;
  }

  public PayComponentCategory getCategory() {
    return category;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getBasis() {
    return basis;
  }
}
