package com.kita.operations.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {
  Optional<UnitOfMeasure> findByCode(String code);

  boolean existsByCode(String code);
}
