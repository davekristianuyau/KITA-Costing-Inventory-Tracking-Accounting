package com.kita.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the KITA operations service (inventory, BOM, production, sales, costing). */
@SpringBootApplication
public class OperationsServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(OperationsServiceApplication.class, args);
  }
}
