package com.kita.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {
  Optional<Client> findByCompanyIdAndActiveTrue(String companyId);

  boolean existsByCompanyId(String companyId);
}
