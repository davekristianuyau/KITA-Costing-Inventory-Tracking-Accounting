package com.kita.operations.common;

import org.springframework.stereotype.Component;

/**
 * Writes the append-only history of significant business events (FR-021).
 *
 * <p>No scrubbing here, unlike hr/crm/procurement: this service holds no statutory identifiers or
 * PII — only opaque customer/supplier refs validated through the party port — and 003 sets no
 * scrubbing requirement. Callers must still keep secrets out of {@code detail}.
 */
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
