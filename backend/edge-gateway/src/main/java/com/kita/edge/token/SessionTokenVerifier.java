package com.kita.edge.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads a session token (as carried in the httpOnly cookie), **JWE-decrypts** it, **verifies the RS256
 * signature** with identity's public key, and checks {@code exp}. Only identity's private key can produce
 * a token this accepts (asymmetric — no shared secret), so no backend can forge one (SC-008, FR-020).
 * Shared by the edge auth filter and reused by each per-client gateway (US2, T021).
 */
@Component
public class SessionTokenVerifier {

  private final EdgeTokenKeys keys;

  public SessionTokenVerifier(EdgeTokenKeys keys) {
    this.keys = keys;
  }

  public record Session(String subject, String client, List<String> roles, Instant expiresAt) {}

  /** Decrypt + verify signature + expiry. Throws {@link InvalidSessionException} on any failure. */
  @SuppressWarnings("unchecked")
  public Session verify(String token) {
    try {
      JWEObject jwe = JWEObject.parse(token);
      jwe.decrypt(new DirectDecrypter(keys.encKey()));
      SignedJWT jws = jwe.getPayload().toSignedJWT();
      if (jws == null || !jws.verify(new RSASSAVerifier(keys.publicKey()))) {
        throw new InvalidSessionException("bad signature");
      }
      JWTClaimsSet c = jws.getJWTClaimsSet();
      Date exp = c.getExpirationTime();
      if (exp == null || exp.toInstant().isBefore(Instant.now())) {
        throw new InvalidSessionException("expired");
      }
      Object roles = c.getClaim("roles");
      return new Session(
          c.getSubject(),
          (String) c.getClaim("client"),
          roles instanceof List ? (List<String>) roles : List.of(),
          exp.toInstant());
    } catch (ParseException | JOSEException e) {
      throw new InvalidSessionException("unparseable token");
    }
  }

  /** Raised when a token cannot be decrypted, verified, or has expired. */
  public static class InvalidSessionException extends RuntimeException {
    public InvalidSessionException(String message) {
      super(message);
    }
  }
}
