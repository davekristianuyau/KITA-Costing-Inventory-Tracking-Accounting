package com.kita.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** KITA procurement-service: supplier records, purchase orders, receiving, and restock. */
@SpringBootApplication
public class ProcurementServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ProcurementServiceApplication.class, args);
  }
}
