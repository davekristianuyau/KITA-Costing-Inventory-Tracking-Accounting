package com.kita.edge;

import com.kita.session.SessionVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared {@link SessionVerifier} from the public + enc keys the edge shares with identity
 * (identity signs with the matching private key; the edge only verifies + decrypts, never mints).
 */
@Configuration
public class EdgeSecurityConfig {

  @Bean
  SessionVerifier sessionVerifier(
      @Value("${edge.jwt.public-key:}") String publicKey,
      @Value("${edge.jwt.enc-key:}") String encKey) {
    return SessionVerifier.fromBase64(publicKey, encKey);
  }
}
