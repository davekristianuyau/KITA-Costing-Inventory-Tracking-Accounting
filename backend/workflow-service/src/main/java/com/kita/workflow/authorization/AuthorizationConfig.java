package com.kita.workflow.authorization;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the pure {@link ActionAuthorizer} from the seeded {@code authorization_mapping} rows. */
@Configuration
public class AuthorizationConfig {

  @Bean
  public ActionAuthorizer actionAuthorizer(AuthorizationMappingRepository repository) {
    List<AuthorizationRule> rules =
        repository.findAll().stream().map(AuthorizationMapping::toRule).toList();
    return new ActionAuthorizer(rules);
  }
}
