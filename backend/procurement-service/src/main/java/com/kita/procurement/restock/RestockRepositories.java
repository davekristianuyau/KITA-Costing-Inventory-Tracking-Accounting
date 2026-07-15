package com.kita.procurement.restock;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositories for the restock module (grouped; each is a top-level interface). */
public final class RestockRepositories {
  private RestockRepositories() {}
}

interface RestockSuggestionRepository extends JpaRepository<RestockSuggestion, UUID> {
  List<RestockSuggestion> findByStatus(RestockStatus status);
}

interface RestockSuggestionLineRepository extends JpaRepository<RestockSuggestionLine, UUID> {
  List<RestockSuggestionLine> findBySuggestionId(UUID suggestionId);
}
