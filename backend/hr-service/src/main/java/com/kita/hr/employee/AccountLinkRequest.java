package com.kita.hr.employee;

import jakarta.validation.constraints.NotBlank;

/** Link an account to an employee (017 FR-008). The name is permanent and never reissued (FR-016). */
public record AccountLinkRequest(@NotBlank String accountUsername) {}
