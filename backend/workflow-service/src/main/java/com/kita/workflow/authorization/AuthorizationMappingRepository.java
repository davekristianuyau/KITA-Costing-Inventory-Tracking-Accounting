package com.kita.workflow.authorization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Reads the seeded role→action grants. */
public interface AuthorizationMappingRepository
    extends JpaRepository<AuthorizationMapping, UUID> {}
