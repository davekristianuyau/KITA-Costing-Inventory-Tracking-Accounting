package com.kita.hr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kita.hr.support.AbstractHrIT;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * T057: {@code contracts/hr-openapi.yaml} is the source of truth. Every documented operation must be
 * routed by the app, and every routed operation must be documented — so the contract and the code
 * cannot drift apart in either direction.
 */
class OpenApiContractTest extends AbstractHrIT {

  private static final Path CONTRACT =
      Path.of("..", "..", "specs", "004-hr-payroll", "contracts", "hr-openapi.yaml");

  /** Served by Actuator, not by @RequestMapping, so it is asserted separately. */
  private static final String HEALTH = "/actuator/health";

  // Qualified by name: Actuator contributes a second RequestMappingHandlerMapping bean.
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @SuppressWarnings("unchecked")
  private Map<String, Object> contract() throws IOException {
    assertThat(CONTRACT).as("OpenAPI contract file").exists();
    try (InputStream in = Files.newInputStream(CONTRACT)) {
      return (Map<String, Object>) new Yaml().load(in);
    }
  }

  @SuppressWarnings("unchecked")
  private Set<String> documentedOperations() throws IOException {
    Map<String, Object> root = contract();
    List<Map<String, Object>> servers = (List<Map<String, Object>>) root.get("servers");
    String base = servers.get(0).get("url").toString();

    Set<String> ops = new TreeSet<>();
    Map<String, Object> paths = (Map<String, Object>) root.get("paths");
    for (Map.Entry<String, Object> path : paths.entrySet()) {
      String url = path.getKey().equals(HEALTH) ? HEALTH : base + path.getKey();
      for (String method : ((Map<String, Object>) path.getValue()).keySet()) {
        ops.add(method.toUpperCase() + " " + url);
      }
    }
    return ops;
  }

  /** Every {@code /api/hr/**} operation Spring actually routes. */
  private Set<String> routedOperations() {
    Set<String> ops = new TreeSet<>();
    handlerMapping
        .getHandlerMethods()
        .forEach(
            (info, method) -> {
              Set<String> patterns = new LinkedHashSet<>();
              if (info.getPathPatternsCondition() != null) {
                patterns.addAll(info.getPathPatternsCondition().getPatternValues());
              } else if (info.getPatternsCondition() != null) {
                patterns.addAll(info.getPatternsCondition().getPatterns());
              }
              for (String pattern : patterns) {
                if (!pattern.startsWith("/api/hr")) {
                  continue; // error pages and other framework routes are not part of the contract
                }
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                  ops.add(m.name() + " " + pattern);
                }
              }
            });
    return ops;
  }

  @Test
  void everyDocumentedOperationIsImplemented() throws IOException {
    Set<String> documented = new TreeSet<>(documentedOperations());
    documented.remove("GET " + HEALTH); // covered by healthEndpointIsUp
    assertThat(routedOperations()).containsAll(documented);
  }

  @Test
  void everyImplementedOperationIsDocumented() throws IOException {
    assertThat(documentedOperations()).containsAll(routedOperations());
  }

  @Test
  void healthEndpointIsUp() throws Exception {
    mockMvc.perform(get(HEALTH)).andExpect(status().isOk());
  }
}
