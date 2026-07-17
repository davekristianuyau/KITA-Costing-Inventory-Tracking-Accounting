package com.kita.workflow.actor;

import com.kita.workflow.common.security.Role;
import java.util.Set;

/** An HR-validated acting employee and the roles HR assigned them. */
public record ResolvedActor(String employeeId, Set<Role> roles) {}
