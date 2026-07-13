package com.kita.hr.payroll;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayPeriodRepository extends JpaRepository<PayPeriod, UUID> {}
