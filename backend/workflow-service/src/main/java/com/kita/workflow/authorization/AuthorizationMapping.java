package com.kita.workflow.authorization;

import com.kita.workflow.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Seeded role→action grant (Flyway V2). The maker/checker split lives in {@code kind}. */
@Entity
@Table(name = "authorization_mapping")
public class AuthorizationMapping {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BackOfficeAction action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AuthorizationKind kind;

  protected AuthorizationMapping() {}

  public AuthorizationRule toRule() {
    return new AuthorizationRule(action, role, kind);
  }
}
