package com.kita.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A tenant: its login company identifier, isolated backend endpoint, and cloud preference. */
@Entity
@Table(name = "client")
public class Client {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "company_id", nullable = false, unique = true)
  private String companyId;

  @Column(nullable = false)
  private String name;

  @Column(name = "cloud_preference", nullable = false)
  private String cloudPreference;

  @Column(name = "backend_endpoint", nullable = false)
  private String backendEndpoint;

  @Column(nullable = false)
  private boolean active = true;

  protected Client() {}

  public Client(String companyId, String name, String cloudPreference, String backendEndpoint) {
    this.companyId = companyId;
    this.name = name;
    this.cloudPreference = cloudPreference;
    this.backendEndpoint = backendEndpoint;
  }

  public UUID getId() {
    return id;
  }

  public String getCompanyId() {
    return companyId;
  }

  public String getName() {
    return name;
  }

  public String getCloudPreference() {
    return cloudPreference;
  }

  public String getBackendEndpoint() {
    return backendEndpoint;
  }

  public boolean isActive() {
    return active;
  }
}
