package com.kita.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** KITA crm-service: customer records, entitlements, loyalty, and discount computation. */
@SpringBootApplication
public class CrmServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CrmServiceApplication.class, args);
  }
}
