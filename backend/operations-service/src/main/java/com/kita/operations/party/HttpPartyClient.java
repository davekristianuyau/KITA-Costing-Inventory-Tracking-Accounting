package com.kita.operations.party;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls the Party master-data service to validate customer/supplier references. Active when
 * {@code operations.party.stub=false}. Fails safe: unreachable/errored lookups are treated as
 * invalid so no operation proceeds on an unverified party.
 */
@Component
@ConditionalOnProperty(name = "operations.party.stub", havingValue = "false")
public class HttpPartyClient implements PartyClient {

  private record PartyDto(Boolean active) {}

  private final RestClient http;

  public HttpPartyClient(@Value("${operations.party.base-url}") String baseUrl) {
    this.http = RestClient.create(baseUrl);
  }

  @Override
  public PartyStatus validateCustomer(String customerRef) {
    return check("/api/party/customers/", customerRef);
  }

  @Override
  public PartyStatus validateSupplier(String supplierRef) {
    return check("/api/party/suppliers/", supplierRef);
  }

  private PartyStatus check(String path, String ref) {
    if (ref == null || ref.isBlank()) {
      return new PartyStatus(false, false);
    }
    try {
      PartyDto dto = http.get().uri(path + ref).retrieve().body(PartyDto.class);
      boolean active = dto == null || dto.active() == null || dto.active();
      return new PartyStatus(true, active);
    } catch (RestClientException e) {
      return new PartyStatus(false, false); // 404 / network / server error -> reject
    }
  }
}
