package com.kita.workflow.security;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Query access to persisted service-identity refusals (018 FR-008). */
public interface ServiceCallRefusalRepository extends JpaRepository<ServiceCallRefusal, UUID> {}
