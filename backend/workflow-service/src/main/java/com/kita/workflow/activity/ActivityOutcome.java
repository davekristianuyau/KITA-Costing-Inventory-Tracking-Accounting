package com.kita.workflow.activity;

/** The terminal outcome of a back-office action, recorded for every attempt (FR-003, SC-003). */
public enum ActivityOutcome {
  SUCCESS,
  REJECTED_NOT_PERMITTED,
  REJECTED_INVALID,
  FAILED_UNAVAILABLE
}
