package com.kita.workflow.authorization;

import com.kita.workflow.common.security.Role;

/** One (action, role, kind) grant — the in-memory shape of an {@code authorization_mapping} row. */
public record AuthorizationRule(BackOfficeAction action, Role role, AuthorizationKind kind) {}
