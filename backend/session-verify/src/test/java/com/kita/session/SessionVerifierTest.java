package com.kita.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the asymmetric token contract: a token minted with identity's private key is accepted and its
 * claims extracted; a token signed with any OTHER key (a backend forging its own) is rejected (SC-008);
 * an expired token is rejected; garbage is rejected.
 */
class SessionVerifierTest {

  private static KeyPair identityKeys;
  private static byte[] encKey;
  private static SessionVerifier verifier;

  @BeforeAll
  static void setup() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    identityKeys = gen.generateKeyPair();
    encKey = new byte[32];
    new SecureRandom().nextBytes(encKey);

    String pubB64 = Base64.getEncoder().encodeToString(identityKeys.getPublic().getEncoded());
    String encB64 = Base64.getEncoder().encodeToString(encKey);
    verifier = SessionVerifier.fromBase64(pubB64, encB64);
  }

  @Test
  void acceptsTokenMintedByIdentityAndExtractsClaims() throws Exception {
    String token =
        mint(
            (RSAPrivateKey) identityKeys.getPrivate(),
            encKey,
            "alice",
            "client-a",
            List.of("USER"),
            Instant.now().plus(Duration.ofMinutes(90)));

    SessionToken session = verifier.verify(token);

    assertThat(session.subject()).isEqualTo("alice");
    assertThat(session.client()).isEqualTo("client-a");
    assertThat(session.roles()).containsExactly("USER");
  }

  @Test
  void rejectsTokenSignedWithAnotherKey() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    RSAPrivateKey forgedKey = (RSAPrivateKey) gen.generateKeyPair().getPrivate();

    String forged =
        mint(
            forgedKey,
            encKey,
            "mallory",
            "client-b",
            List.of("USER"),
            Instant.now().plus(Duration.ofMinutes(90)));

    assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(InvalidSessionException.class);
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    String expired =
        mint(
            (RSAPrivateKey) identityKeys.getPrivate(),
            encKey,
            "alice",
            "client-a",
            List.of("USER"),
            Instant.now().minus(Duration.ofMinutes(1)));

    assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(InvalidSessionException.class);
  }

  @Test
  void rejectsGarbage() {
    assertThatThrownBy(() -> verifier.verify("not-a-token"))
        .isInstanceOf(InvalidSessionException.class);
  }

  /** Mirror of identity's TokenService#issue: sign RS256 then JWE-encrypt (dir, A256GCM). */
  private static String mint(
      RSAPrivateKey signingKey,
      byte[] enc,
      String subject,
      String client,
      List<String> roles,
      Instant exp)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .claim("client", client)
            .claim("roles", roles)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(exp))
            .jwtID(UUID.randomUUID().toString())
            .build();
    SignedJWT jws = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jws.sign(new RSASSASigner(signingKey));
    JWEObject jwe =
        new JWEObject(
            new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).contentType("JWT").build(),
            new Payload(jws));
    jwe.encrypt(new DirectEncrypter(enc));
    return jwe.serialize();
  }
}
