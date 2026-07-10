package com.kita.operations.production;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildRepository extends JpaRepository<Build, UUID> {}
