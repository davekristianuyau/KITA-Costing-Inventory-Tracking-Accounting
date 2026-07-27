package com.kita.workflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a refusal in its own transaction (018 FR-008). {@code REQUIRES_NEW} because the refusal is
 * written from a servlet filter, before any request transaction exists and while the request is being
 * rejected — the record must survive regardless of what happens to the refused request.
 *
 * <p>Recording must never turn a refusal into a 500: if the write fails the caller is still refused,
 * and the failure is logged loudly.
 */
@Service
public class ServiceCallRefusalRecorder {

  private static final Logger log = LoggerFactory.getLogger(ServiceCallRefusalRecorder.class);

  private final ServiceCallRefusalRepository repository;

  public ServiceCallRefusalRecorder(ServiceCallRefusalRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(ServiceCallRefusal refusal) {
    try {
      repository.save(refusal);
    } catch (RuntimeException e) {
      log.error(
          "could not persist service-call refusal (reason={} cn={}) — the call is still refused",
          refusal.getReason(),
          refusal.getAttemptedCn(),
          e);
    }
  }
}
