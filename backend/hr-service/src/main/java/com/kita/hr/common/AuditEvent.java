package com.kita.hr.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Append-only audit record (who/when/action). Detail must be PII/secret-scrubbed by the caller. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column private String actor;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_ref")
  private String entityRef;

  @Column(nullable = false)
  private Instant at;

  @Column private String detail;

  protected AuditEvent() {}

  public AuditEvent(String actor, String action, String entityRef, String detail) {
    this.actor = actor;
    this.action = action;
    this.entityRef = entityRef;
    this.detail = detail;
    this.at = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getAction() {
    return action;
  }

  public String getEntityRef() {
    return entityRef;
  }
}
