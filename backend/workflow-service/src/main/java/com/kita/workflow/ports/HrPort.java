package com.kita.workflow.ports;

import java.util.Set;

/**
 * Boundary to hr-service (FR-001, FR-002). Resolves the signed-in <b>account</b> to the employee it
 * belongs to, and supplies the status and roles the personnel record holds. workflow-service never
 * caches this (no duplicate master) and never trusts a self-asserted role header.
 *
 * <p><b>017 change.</b> The lookup key is the <em>account username</em> — what {@code X-Kita-User}
 * actually carries — not an HR UUID. That mismatch is the bug this feature exists to fix.
 *
 * <p>The result is a {@link ResolutionOutcome}, not an {@code Optional}: an empty Optional collapsed
 * "no employee linked", "employee missing" and "employee inactive" into one indistinguishable value,
 * and SC-004 requires each to be reported separately — none of them may become "not permitted" (403).
 */
public interface HrPort {

  /** Resolve the account to an actable employee. Never throws for a business outcome. */
  ResolutionOutcome resolve(String accountUsername);

  /** Why resolution succeeded or failed. */
  enum Status {
    /** An active employee is linked; roles are populated. */
    RESOLVED,
    /** The account exists but no employee is linked to it (FR-005). */
    NO_EMPLOYEE_LINKED,
    /** Linked, but the employee is inactive/separated — the reason names the status (FR-006). */
    EMPLOYEE_NOT_ACTIVE,
    /** The link points at an employee record that no longer exists (FR-006). */
    EMPLOYEE_MISSING,
    /** hr could not be reached. <b>Fail closed</b> — never grant on a failed lookup (FR-011). */
    UNAVAILABLE
  }

  /**
   * The outcome of resolving one account. {@code employeeId} and {@code roles} are populated only when
   * {@link Status#RESOLVED}; {@code detail} carries the specific reason otherwise (e.g. the employee's
   * actual status), so the caller can say what went wrong rather than give a generic refusal.
   */
  record ResolutionOutcome(Status status, String employeeId, Set<String> roles, String detail) {

    public static ResolutionOutcome resolved(String employeeId, Set<String> roles) {
      return new ResolutionOutcome(Status.RESOLVED, employeeId, Set.copyOf(roles), null);
    }

    public static ResolutionOutcome noEmployeeLinked(String account) {
      return new ResolutionOutcome(
          Status.NO_EMPLOYEE_LINKED, null, Set.of(), "no employee is linked to account " + account);
    }

    public static ResolutionOutcome notActive(String employeeId, String employeeStatus) {
      return new ResolutionOutcome(
          Status.EMPLOYEE_NOT_ACTIVE, employeeId, Set.of(), "employee is " + employeeStatus);
    }

    public static ResolutionOutcome missing(String account) {
      return new ResolutionOutcome(
          Status.EMPLOYEE_MISSING,
          null,
          Set.of(),
          "the employee linked to account " + account + " no longer exists");
    }

    public static ResolutionOutcome unavailable(String detail) {
      return new ResolutionOutcome(Status.UNAVAILABLE, null, Set.of(), detail);
    }

    public boolean isResolved() {
      return status == Status.RESOLVED;
    }
  }
}
