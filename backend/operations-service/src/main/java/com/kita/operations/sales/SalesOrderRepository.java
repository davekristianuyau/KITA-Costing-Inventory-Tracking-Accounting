package com.kita.operations.sales;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {}
