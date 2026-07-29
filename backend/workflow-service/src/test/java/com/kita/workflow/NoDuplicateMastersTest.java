package com.kita.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Guards FR-017: workflow-service persists only its own tables and must NOT introduce a JPA entity
 * that duplicates any domain master (customer, supplier, employee, order, PO, stock…). If a new
 * {@code @Entity} appears here, that is either the intended store or a master-duplication bug — decide
 * deliberately, don't let it slip in.
 *
 * <p>Deliberate additions so far: {@code ServiceCallRefusal} (018 FR-008) — a security audit record of
 * callers this service refused. It is owned here and duplicates no domain master.
 */
class NoDuplicateMastersTest {

  @Test
  void onlyTheOwnedEntitiesArePersisted() {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    Set<String> entities =
        scanner.findCandidateComponents("com.kita.workflow").stream()
            .map(bd -> bd.getBeanClassName())
            .collect(Collectors.toSet());

    assertThat(entities)
        .containsExactlyInAnyOrder(
            "com.kita.workflow.activity.ActivityRecord",
            "com.kita.workflow.authorization.AuthorizationMapping",
            "com.kita.workflow.security.ServiceCallRefusal");
  }
}
