package com.kita.crm.discount;

/**
 * How a rule treats VAT. {@code VAT_EXEMPT} computes the discount on the VAT-exclusive base — PH
 * senior/PWD strip VAT before applying the statutory percentage.
 */
public enum VatTreatment {
  NONE,
  VAT_EXEMPT
}
