package com.kita.edge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 017 FR-018/FR-011 — the edge resolves roles from the personnel record on every request.
 *
 * <p>The asymmetry between "cannot check" and "checked, nothing granted" is the point of these tests.
 * An unreachable personnel system must refuse the request outright; an account that resolves to nobody
 * actable must yield <em>no roles</em> and let the receiving service refuse on its own terms. Collapsing
 * the two would either grant on failure or make an ordinary refusal look like an outage.
 */
class RoleResolverTest {

  private MockWebServer hr;

  @BeforeEach
  void setUp() throws Exception {
    hr = new MockWebServer();
    hr.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    hr.shutdown();
  }

  private RoleResolver resolver() {
    String base = hr.url("/").toString();
    return new RoleResolver(WebClient.builder(), base.substring(0, base.length() - 1), 2000);
  }

  private void respond(int code, String body) {
    hr.enqueue(
        new MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
  }

  @Test
  void anActiveEmployeeYieldsTheRolesThePersonnelRecordHolds() {
    respond(200, "{\"id\":\"emp-1\",\"status\":\"ACTIVE\",\"roles\":[\"SALES\",\"CASHIER\"]}");

    assertThat(resolver().rolesFor("alice").block()).containsExactly("SALES", "CASHIER");
  }

  @Test
  void aRoleChangeIsVisibleOnTheNextRequestWithNoReLogin() {
    respond(200, "{\"id\":\"emp-1\",\"status\":\"ACTIVE\",\"roles\":[\"SALES\"]}");
    respond(200, "{\"id\":\"emp-1\",\"status\":\"ACTIVE\",\"roles\":[\"CASHIER\"]}");

    RoleResolver resolver = resolver();
    assertThat(resolver.rolesFor("alice").block()).containsExactly("SALES");

    // Nothing about the session changed — only the personnel record. If anything cached, this fails,
    // and a revoked role would keep working until the cache expired (SC-002/SC-006).
    assertThat(resolver.rolesFor("alice").block()).containsExactly("CASHIER");
  }

  @Test
  void anAccountWithNoEmployeeYieldsNoRolesRatherThanAnError() {
    respond(404, "");

    // Not an outage: the request proceeds and the receiving service refuses it on its own terms,
    // keeping "you may not" distinguishable from "we could not check".
    assertThat(resolver().rolesFor("nobody").block()).isEqualTo(List.of());
  }

  @Test
  void anInactiveEmployeeYieldsNoRoles() {
    respond(200, "{\"id\":\"emp-1\",\"status\":\"SEPARATED\",\"roles\":[\"SALES\"]}");

    assertThat(resolver().rolesFor("bob").block()).isEqualTo(List.of());
  }

  @Test
  void anUnreachablePersonnelSystemFailsClosed() throws Exception {
    hr.shutdown(); // nothing listening

    assertThatThrownBy(() -> resolver().rolesFor("alice").block())
        .as("never grant on a failed lookup (FR-011)")
        .isInstanceOf(RoleResolver.Unavailable.class);
  }

  @Test
  void aServerErrorFromHrFailsClosedToo() {
    respond(500, "");

    assertThatThrownBy(() -> resolver().rolesFor("alice").block())
        .isInstanceOf(RoleResolver.Unavailable.class);
  }

  @Test
  void aBlankAccountYieldsNoRolesWithoutCallingHr() {
    assertThat(resolver().rolesFor("  ").block()).isEqualTo(List.of());
    assertThat(hr.getRequestCount()).isZero();
  }
}
