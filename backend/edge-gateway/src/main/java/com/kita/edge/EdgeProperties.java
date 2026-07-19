package com.kita.edge;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Edge configuration: the session cookie name and the client → backend-gateway map. Only clients present
 * in {@code backends} are routable; a validated token whose {@code client} is absent is rejected (401).
 */
@ConfigurationProperties(prefix = "edge")
public class EdgeProperties {

  /** Validated {@code client} claim → that client's backend gateway URL (e.g. http://client-a-gateway:8081). */
  private Map<String, String> backends = new LinkedHashMap<>();

  private final Cookie cookie = new Cookie();

  public Map<String, String> getBackends() {
    return backends;
  }

  public void setBackends(Map<String, String> backends) {
    this.backends = backends;
  }

  public Cookie getCookie() {
    return cookie;
  }

  public static class Cookie {
    private String name = "kita_session";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
