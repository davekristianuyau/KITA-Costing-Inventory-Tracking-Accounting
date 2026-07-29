package com.kita.workflow.ports.http;

import org.springframework.boot.autoconfigure.web.client.RestClientSsl;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Makes every outbound call from this service present its own service identity and trust the internal
 * CA (018 US3, FR-007/008). Applied to the shared {@code RestClient.Builder}, so all four http adapters
 * inherit it without each having to know about TLS.
 *
 * <p>Only active under the {@code mtls} profile (the composed and Floci stacks) — local runs and the
 * adapter unit tests keep using plain HTTP against MockWebServer.
 */
@Configuration
@Profile("mtls")
public class MutualTlsClientConfig {

  /** Binds the {@code service} SSL bundle (key = our identity, truststore = the internal CA). */
  @Bean
  RestClientCustomizer mutualTlsRestClientCustomizer(RestClientSsl ssl) {
    return builder -> builder.apply(ssl.fromBundle("service"));
  }
}
