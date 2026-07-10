package com.kita.operations.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simple service health/version endpoint matching the OpenAPI contract. */
@RestController
@RequestMapping("/api/operations")
public class SystemController {

  @Value("${spring.application.name:operations-service}")
  private String appName;

  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP", "version", appName);
  }
}
