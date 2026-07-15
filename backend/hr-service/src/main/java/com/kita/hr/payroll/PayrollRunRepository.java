package com.kita.hr.payroll;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

  boolean existsByIdempotencyKey(String idempotencyKey);

  /** Pessimistic lock so a concurrent finalize of the same run serializes (no double-finalize). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from PayrollRun r where r.id = :id")
  Optional<PayrollRun> findByIdForUpdate(@Param("id") UUID id);
}
