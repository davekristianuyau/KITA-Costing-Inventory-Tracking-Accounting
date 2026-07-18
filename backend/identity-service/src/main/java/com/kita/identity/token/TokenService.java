package com.kita.identity.token;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and parses session tokens: a JWT **signed RS256** (only identity's private key can mint one) then
 * **JWE-encrypted** (A256GCM), with a 90-minute lifetime (009 clarifications, FR-020/021). The edge and each
 * client backend verify with the public key + decrypt with the shared enc key.
 */
@Service
public class TokenService {

  private final TokenKeys keys;
  private final long ttlMinutes;

  public TokenService(TokenKeys keys, @Value("${identity.jwt.ttl-minutes:90}") long ttlMinutes) {
    this.keys = keys;
    this.ttlMinutes = ttlMinutes;
  }

  public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {}

  public record ParsedToken(String subject, String client, List<String> roles, Instant expiresAt) {}

  /** Sign (RS256) then encrypt (JWE). */
  public IssuedToken issue(String subject, String client, List<String> roles) {
    Instant now = Instant.now();
    Instant exp = now.plus(Duration.ofMinutes(ttlMinutes));
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .claim("client", client)
            .claim("roles", roles)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .jwtID(UUID.randomUUID().toString())
            .build();
    try {
      SignedJWT jws = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jws.sign(new RSASSASigner(keys.privateKey()));
      JWEObject jwe =
          new JWEObject(
              new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                  .contentType("JWT")
                  .build(),
              new Payload(jws));
      jwe.encrypt(new DirectEncrypter(keys.encKey()));
      return new IssuedToken(jwe.serialize(), exp, ttlMinutes * 60);
    } catch (JOSEException e) {
      throw new IllegalStateException("failed to issue token", e);
    }
  }

  /** Decrypt + verify signature + expiry. Throws {@link InvalidTokenException} on any failure. */
  @SuppressWarnings("unchecked")
  public ParsedToken parse(String token) {
    try {
      JWEObject jwe = JWEObject.parse(token);
      jwe.decrypt(new DirectDecrypter(keys.encKey()));
      SignedJWT jws = jwe.getPayload().toSignedJWT();
      if (jws == null || !jws.verify(new RSASSAVerifier(keys.publicKey()))) {
        throw new InvalidTokenException("bad signature");
      }
      JWTClaimsSet c = jws.getJWTClaimsSet();
      Date exp = c.getExpirationTime();
      if (exp == null || exp.toInstant().isBefore(Instant.now())) {
        throw new InvalidTokenException("expired");
      }
      Object roles = c.getClaim("roles");
      return new ParsedToken(
          c.getSubject(),
          (String) c.getClaim("client"),
          roles instanceof List ? (List<String>) roles : List.of(),
          exp.toInstant());
    } catch (ParseException | JOSEException e) {
      throw new InvalidTokenException("unparseable token");
    }
  }

  /** Raised when a token cannot be decrypted, verified, or has expired. */
  public static class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
      super(message);
    }
  }
}
