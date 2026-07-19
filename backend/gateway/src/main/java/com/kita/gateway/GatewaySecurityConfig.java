package com.kita.gateway;

import com.kita.session.SessionVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared {@link SessionVerifier} from the same public + enc keys identity signs with, so this
 * per-client gateway can independently verify a session token (defense-in-depth behind the edge).
 */
@Configuration
public class GatewaySecurityConfig {

  @Bean
  SessionVerifier sessionVerifier(
      @Value("${gateway.jwt.public-key:}") String publicKey,
      @Value("${gateway.jwt.enc-key:}") String encKey) {
    return SessionVerifier.fromBase64(publicKey, encKey);
  }
}
