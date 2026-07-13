package com.kita.hr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the KITA HR & payroll service (employees, attendance, payroll, deductions, leave). */
@SpringBootApplication
public class HrServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(HrServiceApplication.class, args);
  }
}
