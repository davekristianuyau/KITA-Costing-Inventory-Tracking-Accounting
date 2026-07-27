package com.kita.hr.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A persisted record of an internal caller this service refused for an identity/transport reason
 * (018 FR-008). Durable and queryable on purpose: if someone probes the internal network we want to
 * know afterwards, not just at the moment a log line scrolled past.
 *
 * <p>Distinct from a business rejection (422, has a business reason) and from an actor-permission
 * refusal (403 from {@code CallerContext.require(Role)}) — this is a <em>service identity</em> refusal.
 */
@Entity
@Table(name = "service_call_refusal")
public class ServiceCallRefusal {

  /** Why the caller could not be verified. */
  public enum Reason {
    /** No client certificate presented (the common probe — recordable thanks to client-auth=want). */
    NO_CERT,
    /** A certificate that does not chain to the trusted CA. */
    UNTRUSTED_CA,
    /** A CA-signed certificate that is outside its validity window. */
    EXPIRED,
    /** Validly signed, but its subject is not an expected peer service (impersonation). */
    NOT_ALLOWLISTED
  }

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "peer_address")
  private String peerAddress;

  /** Null when no certificate was presented. */
  @Column(name = "attempted_cn")
  private String attemptedCn;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "request_method")
  private String requestMethod;

  @Column(name = "request_path")
  private String requestPath;

  protected ServiceCallRefusal() {}

  public ServiceCallRefusal(
      Instant occurredAt,
      String peerAddress,
      String attemptedCn,
      Reason reason,
      String requestMethod,
      String requestPath) {
    this.occurredAt = occurredAt;
    this.peerAddress = peerAddress;
    this.attemptedCn = attemptedCn;
    this.reason = reason.name();
    this.requestMethod = requestMethod;
    this.requestPath = requestPath;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getPeerAddress() {
    return peerAddress;
  }

  public String getAttemptedCn() {
    return attemptedCn;
  }

  public String getReason() {
    return reason;
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public String getRequestPath() {
    return requestPath;
  }
}
