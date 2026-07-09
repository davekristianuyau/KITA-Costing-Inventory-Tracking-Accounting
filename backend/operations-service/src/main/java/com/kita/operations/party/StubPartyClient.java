package com.kita.operations.party;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Permissive stub used until the real Party service exists (dev/test only). A non-blank reference
 * is valid, EXCEPT the sentinels "invalid"/"unknown" (case-insensitive) which are treated as
 * non-existent so the party-rejection path can be exercised in tests. Enabled by
 * {@code operations.party.stub=true} (the default in dev).
 */
@Component
@ConditionalOnProperty(name = "operations.party.stub", havingValue = "true", matchIfMissing = true)
public class StubPartyClient implements PartyClient {

  @Override
  public PartyStatus validateCustomer(String customerRef) {
    return refStatus(customerRef);
  }

  @Override
  public PartyStatus validateSupplier(String supplierRef) {
    return refStatus(supplierRef);
  }

  private PartyStatus refStatus(String ref) {
    boolean present =
        ref != null
            && !ref.isBlank()
            && !ref.equalsIgnoreCase("invalid")
            && !ref.equalsIgnoreCase("unknown");
    return new PartyStatus(present, present);
  }
}
