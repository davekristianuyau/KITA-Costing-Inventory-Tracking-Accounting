package com.kita.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A user; belongs to exactly one client; username unique within that client (FR-019). */
@Entity
@Table(
    name = "app_user",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "username"}))
public class AppUser {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "client_id", nullable = false)
  private Client client;

  @Column(nullable = false)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts = 0;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  protected AppUser() {}

  public AppUser(Client client, String username, String passwordHash) {
    this.client = client;
    this.username = username;
    this.passwordHash = passwordHash;
  }

  public UUID getId() {
    return id;
  }

  public Client getClient() {
    return client;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return active;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void recordFailure(int maxAttempts, java.time.Duration lockFor, Instant now) {
    this.failedAttempts++;
    if (this.failedAttempts >= maxAttempts) {
      this.lockedUntil = now.plus(lockFor);
      this.failedAttempts = 0;
    }
  }

  public void recordSuccess() {
    this.failedAttempts = 0;
    this.lockedUntil = null;
  }
}
