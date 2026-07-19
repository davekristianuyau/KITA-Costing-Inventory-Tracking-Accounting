package com.kita.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** T011 [US1]: login authenticates and issues an httpOnly session cookie; failures are non-revealing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(
    properties = {
      "identity.seed.demo-password=demo-pass",
      "identity.security.max-failed-attempts=3",
      "identity.security.lock-minutes=15"
    })
class AuthControllerIT {

  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired MockMvc mvc;

  private static String body(String company, String username, String password) {
    return "{\"company\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}"
        .formatted(company, username, password);
  }

  @Test
  void validLoginIssuesHttpOnlySessionCookie() throws Exception {
    MvcResult res =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("client-a", "alice", "demo-pass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.client").value("client-a"))
            .andExpect(jsonPath("$.expiresIn").value(5400))
            .andReturn();
    String setCookie = res.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("kita_session=").contains("HttpOnly");
    assertThat(setCookie).doesNotContain("demo-pass"); // token, not the password
  }

  @Test
  void invalidPasswordIsUnauthorizedAndNonRevealing() throws Exception {
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("client-a", "alice", "wrong")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("invalid credentials"));
  }

  @Test
  void unknownUserIsUnauthorized() throws Exception {
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("client-a", "ghost", "demo-pass")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void repeatedFailuresLockTheAccount() throws Exception {
    for (int i = 0; i < 3; i++) {
      mvc.perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("client-b", "bob", "wrong")))
          .andExpect(status().isUnauthorized());
    }
    // now locked → 423 even for the correct password
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("client-b", "bob", "demo-pass")))
        .andExpect(status().isLocked());
  }
}
