package com.kita.workflow.ports.http;

import com.kita.workflow.common.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Deterministic derivation of values a receiving service requires but no human supplies (018 FR-002).
 * Everything here is a pure function of its inputs, so a retry of the same action derives the same
 * value and never creates a second record. The service-backed lookups (fetching the catalog / location
 * list) live in the adapters; this class holds the deterministic resolution + code generation so it can
 * be unit-tested without a live service.
 */
public final class DerivedValues {

  private DerivedValues() {}

  /** An item as the operations catalog reports it (sku ↔ UUID). */
  public record ItemOption(String sku, UUID id) {}

  /** A stock location as operations reports it (code ↔ UUID). */
  public record LocationOption(String code, UUID id) {}

  /** Location codes we treat as the default finished-goods location, in preference order. */
  private static final List<String> PREFERRED_LOCATION_CODES = List.of("MAIN", "WH-MAIN", "WAREHOUSE");

  /** Resolve an item ref (sku) to its operations UUID; unknown ref is a business rejection. */
  public static UUID resolveSku(String sku, List<ItemOption> catalog) {
    return catalog.stream()
        .filter(i -> i.sku().equalsIgnoreCase(sku))
        .map(ItemOption::id)
        .findFirst()
        .orElseThrow(() -> new ValidationException("unknown item: " + sku));
  }

  /**
   * Pick the default location deterministically: a preferred code if present, else the
   * lowest code alphabetically. Empty list is a business rejection (no location to build into).
   */
  public static UUID defaultLocation(List<LocationOption> locations) {
    if (locations.isEmpty()) {
      throw new ValidationException("no stock location configured to build into");
    }
    for (String preferred : PREFERRED_LOCATION_CODES) {
      for (LocationOption loc : locations) {
        if (loc.code().equalsIgnoreCase(preferred)) {
          return loc.id();
        }
      }
    }
    return locations.stream()
        .min((a, b) -> a.code().compareToIgnoreCase(b.code()))
        .orElseThrow()
        .id();
  }

  /**
   * A stable supplier code derived from the name: uppercased, non-alphanumerics collapsed to a single
   * dash, trimmed, capped. Deterministic — the same name always yields the same code, so a retried
   * create cannot make a second supplier (FR-002). Blank name is a business rejection.
   */
  public static String supplierCode(String name) {
    if (name == null || name.isBlank()) {
      throw new ValidationException("supplier name is required to derive a code");
    }
    String code =
        name.trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    if (code.length() > 32) {
      code = code.substring(0, 32).replaceAll("-+$", "");
    }
    return code.isEmpty() ? "SUP" : code;
  }
}
