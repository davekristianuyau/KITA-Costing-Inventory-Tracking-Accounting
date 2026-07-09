package com.kita.operations.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UomConversionRepository extends JpaRepository<UomConversion, UUID> {
  Optional<UomConversion> findByFromUomAndToUom(UnitOfMeasure fromUom, UnitOfMeasure toUom);
}
