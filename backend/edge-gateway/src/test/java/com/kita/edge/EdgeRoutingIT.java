package com.kita.edge;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Edge routing + isolation (T017, SC-002/006): a {@code client=A} token routes only to A's backend
 * (B never contacted); inbound {@code X-Kita-*} is stripped and trusted headers set; missing/expired/
 * forged token → 401; unknown client → 401; unreachable backend → 503. Two demo backends are stubbed
 * with MockWebServer and the edge is given the matching verification keys.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EdgeRoutingIT {

  private static final MockWebServer backendA = new MockWebServer();
  private static final MockWebServer backendB = new MockWebServer();
  private static KeyPair identityKeys;
  private static byte[] encKey;
  private static int deadPort;

  @LocalServerPort private int port;
  private WebTestClient web;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    identityKeys = gen.generateKeyPair();
    encKey = new byte[32];
    new SecureRandom().nextBytes(encKey);

    backendA.start();
    backendB.start();
    // A started-then-stopped server yields a port with nothing listening → connection refused.
    MockWebServer dead = new MockWebServer();
    dead.start();
    deadPort = dead.getPort();
    dead.shutdown();

    registry.add("edge.backends.client-a", () -> "http://localhost:" + backendA.getPort());
    registry.add("edge.backends.client-b", () -> "http://localhost:" + backendB.getPort());
    registry.add("edge.backends.client-dead", () -> "http://localhost:" + deadPort);
    registry.add(
        "edge.jwt.public-key",
        () -> Base64.getEncoder().encodeToString(identityKeys.getPublic().getEncoded()));
    registry.add("edge.jwt.enc-key", () -> Base64.getEncoder().encodeToString(encKey));
  }

  @AfterAll
  static void stop() throws Exception {
    backendA.shutdown();
    backendB.shutdown();
  }

  @BeforeEach
  void setUp() {
    web =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Test
  void routesToOwnBackendOnlyAndSetsTrustedHeaders() throws Exception {
    backendA.enqueue(new MockResponse().setResponseCode(200).setBody("A-DATA"));

    web.get()
        .uri("/api/operations/things")
        .cookie("kita_session", mint(privateKey(), "alice", "client-a", validExp()))
        .header("X-Kita-Client", "client-b") // spoof attempt — must be stripped
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("A-DATA");

    RecordedRequest received = backendA.takeRequest(3, TimeUnit.SECONDS);
    assertThat(received).isNotNull();
    assertThat(received.getPath()).isEqualTo("/api/operations/things");
    assertThat(received.getHeader("X-Kita-User")).isEqualTo("alice");
    assertThat(received.getHeader("X-Kita-Client")).isEqualTo("client-a"); // trusted, not the spoof
    assertThat(backendB.getRequestCount()).isZero(); // isolation: B never contacted
  }

  @Test
  void missingCookieIsUnauthorized() {
    web.get().uri("/api/operations/x").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void expiredTokenIsUnauthorized() throws Exception {
    String expired = mint(privateKey(), "alice", "client-a", Instant.now().minusSeconds(60));
    web.get()
        .uri("/api/operations/x")
        .cookie("kita_session", expired)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void tokenForgedWithAnotherKeyIsUnauthorized() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    RSAPrivateKey forged = (RSAPrivateKey) gen.generateKeyPair().getPrivate();

    web.get()
        .uri("/api/operations/x")
        .cookie("kita_session", mint(forged, "mallory", "client-a", validExp()))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void unknownClientIsUnauthorized() throws Exception {
    web.get()
        .uri("/api/operations/x")
        .cookie("kita_session", mint(privateKey(), "alice", "client-unmapped", validExp()))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void unreachableBackendIsServiceUnavailable() throws Exception {
    web.get()
        .uri("/api/operations/x")
        .cookie("kita_session", mint(privateKey(), "alice", "client-dead", validExp()))
        .exchange()
        .expectStatus()
        .isEqualTo(503);
  }

  private static RSAPrivateKey privateKey() {
    return (RSAPrivateKey) identityKeys.getPrivate();
  }

  private static Instant validExp() {
    return Instant.now().plus(Duration.ofMinutes(90));
  }

  /** Mirror of identity TokenService#issue: sign RS256 then JWE-encrypt (dir, A256GCM). */
  private static String mint(RSAPrivateKey signingKey, String subject, String client, Instant exp)
      throws Exception {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .claim("client", client)
            .claim("roles", List.of("USER"))
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
    jwe.encrypt(new DirectEncrypter(encKey));
    return jwe.serialize();
  }
}
