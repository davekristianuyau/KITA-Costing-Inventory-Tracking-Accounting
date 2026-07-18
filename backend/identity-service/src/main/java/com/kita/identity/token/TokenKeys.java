package com.kita.identity.token;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signing + encryption key material. The RSA keypair signs (private) and verifies (public) the token; a
 * 32-byte AES key encrypts it (JWE). When keys are not supplied, ephemeral ones are generated — DEV ONLY,
 * because the edge and every client backend must share the SAME public/enc keys to verify/decrypt (FR-020).
 */
@Component
public class TokenKeys {

  private static final Logger log = LoggerFactory.getLogger(TokenKeys.class);

  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final byte[] encKey;

  public TokenKeys(
      @Value("${identity.jwt.private-key:}") String privB64,
      @Value("${identity.jwt.public-key:}") String pubB64,
      @Value("${identity.jwt.enc-key:}") String encB64) {
    try {
      if (privB64.isBlank() || pubB64.isBlank() || encB64.isBlank()) {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) kp.getPrivate();
        this.publicKey = (RSAPublicKey) kp.getPublic();
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        this.encKey = k;
        log.warn(
            "identity.jwt keys not supplied — generated EPHEMERAL keys (dev only). "
                + "Production and the edge/backends must share configured keys.");
      } else {
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        this.privateKey =
            (RSAPrivateKey)
                rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privB64)));
        this.publicKey =
            (RSAPublicKey)
                rsa.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)));
        this.encKey = Base64.getDecoder().decode(encB64);
      }
    } catch (Exception e) {
      throw new IllegalStateException("failed to initialise token keys", e);
    }
  }

  public RSAPrivateKey privateKey() {
    return privateKey;
  }

  public RSAPublicKey publicKey() {
    return publicKey;
  }

  public byte[] encKey() {
    return encKey;
  }
}
