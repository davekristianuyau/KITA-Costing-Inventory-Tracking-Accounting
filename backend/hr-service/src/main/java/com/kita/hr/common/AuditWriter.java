package com.kita.hr.common;

import org.springframework.stereotype.Component;

/** Writes append-only audit events. Callers must not pass secrets/PII in {@code detail}. */
@Component
public class AuditWriter {

  private final AuditEventRepository repository;

  public AuditWriter(AuditEventRepository repository) {
    this.repository = repository;
  }

  public void record(String actor, String action, String entityRef, String detail) {
    repository.save(new AuditEvent(actor, action, entityRef, detail));
  }
}
