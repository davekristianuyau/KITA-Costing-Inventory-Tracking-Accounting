package com.kita.edge.token;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verification key material shared with identity. The edge holds only the RSA **public** key (verify) and
 * the AES **enc** key (JWE decrypt) — never the private key, so it cannot mint tokens (FR-020). When keys
 * are not supplied, throwaway ephemeral keys are used: verification then **fails closed** for every real
 * token (the sim/production must supply the same keys identity signs with).
 */
@Component
public class EdgeTokenKeys {

  private static final Logger log = LoggerFactory.getLogger(EdgeTokenKeys.class);

  private final RSAPublicKey publicKey;
  private final byte[] encKey;

  public EdgeTokenKeys(
      @Value("${edge.jwt.public-key:}") String pubB64, @Value("${edge.jwt.enc-key:}") String encB64) {
    try {
      if (pubB64.isBlank() || encB64.isBlank()) {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.publicKey = (RSAPublicKey) gen.generateKeyPair().getPublic();
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        this.encKey = k;
        log.warn(
            "edge.jwt keys not supplied — using EPHEMERAL keys; token verification FAILS CLOSED. "
                + "Supply the SAME public/enc keys identity signs with.");
      } else {
        this.publicKey =
            (RSAPublicKey)
                KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)));
        this.encKey = Base64.getDecoder().decode(encB64);
      }
    } catch (Exception e) {
      throw new IllegalStateException("failed to initialise edge token keys", e);
    }
  }

  public RSAPublicKey publicKey() {
    return publicKey;
  }

  public byte[] encKey() {
    return encKey;
  }
}
