package com.kita.crm.common;

import org.springframework.stereotype.Component;

/** Writes append-only audit events. Callers must not pass secrets/PII in {@code detail}. */
@Component
public class AuditWriter {

  private final AuditEventRepository repository;

  public AuditWriter(AuditEventRepository repository) {
    this.repository = repository;
  }

  /** Detail is scrubbed here too, so a careless caller cannot persist a statutory/tax ID. */
  public void record(String actor, String action, String entityRef, String detail) {
    repository.save(new AuditEvent(actor, action, entityRef, LogScrubber.scrub(detail)));
  }
}
