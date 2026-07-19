package com.kita.session;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Verifies a session token: **JWE-decrypt** it, **verify the RS256 signature** with identity's public key,
 * and check {@code exp}. Only identity's private key can produce a token this accepts (asymmetric — no
 * shared secret), so no backend can forge one (SC-008, FR-020). Shared by the edge and each client gateway.
 */
public final class SessionVerifier {

  private static final System.Logger LOG = System.getLogger(SessionVerifier.class.getName());

  private final RSAPublicKey publicKey;
  private final byte[] encKey;

  public SessionVerifier(RSAPublicKey publicKey, byte[] encKey) {
    this.publicKey = publicKey;
    this.encKey = encKey.clone();
  }

  /**
   * Build a verifier from base64 keys. When either is blank, throwaway ephemeral keys are used and every
   * real token is rejected — verification **fails closed** (dev/standalone only; the sim/production must
   * supply the same public + enc keys identity signs with).
   */
  public static SessionVerifier fromBase64(String publicKeyB64, String encKeyB64) {
    try {
      if (publicKeyB64 == null || publicKeyB64.isBlank() || encKeyB64 == null || encKeyB64.isBlank()) {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPublicKey ephemeral = (RSAPublicKey) gen.generateKeyPair().getPublic();
        byte[] enc = new byte[32];
        new SecureRandom().nextBytes(enc);
        LOG.log(
            System.Logger.Level.WARNING,
            "session keys not supplied — using EPHEMERAL keys; token verification FAILS CLOSED.");
        return new SessionVerifier(ephemeral, enc);
      }
      RSAPublicKey pub =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
      return new SessionVerifier(pub, Base64.getDecoder().decode(encKeyB64));
    } catch (Exception e) {
      throw new IllegalStateException("failed to initialise session verifier keys", e);
    }
  }

  /** Decrypt + verify signature + expiry. Throws {@link InvalidSessionException} on any failure. */
  @SuppressWarnings("unchecked")
  public SessionToken verify(String token) {
    try {
      JWEObject jwe = JWEObject.parse(token);
      jwe.decrypt(new DirectDecrypter(encKey));
      SignedJWT jws = jwe.getPayload().toSignedJWT();
      if (jws == null || !jws.verify(new RSASSAVerifier(publicKey))) {
        throw new InvalidSessionException("bad signature");
      }
      JWTClaimsSet c = jws.getJWTClaimsSet();
      Date exp = c.getExpirationTime();
      if (exp == null || exp.toInstant().isBefore(Instant.now())) {
        throw new InvalidSessionException("expired");
      }
      Object roles = c.getClaim("roles");
      return new SessionToken(
          c.getSubject(),
          (String) c.getClaim("client"),
          roles instanceof List ? (List<String>) roles : List.of(),
          exp.toInstant());
    } catch (ParseException | JOSEException e) {
      throw new InvalidSessionException("unparseable token");
    }
  }
}
