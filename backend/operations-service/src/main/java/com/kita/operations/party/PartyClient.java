package com.kita.operations.party;

/**
 * Port for validating customer/supplier references owned by the separate Party master-data
 * service. See specs/003 contracts/party-integration.md.
 */
public interface PartyClient {

  record PartyStatus(boolean exists, boolean active) {
    public boolean isValid() {
      return exists && active;
    }
  }

  PartyStatus validateCustomer(String customerRef);

  PartyStatus validateSupplier(String supplierRef);
}
