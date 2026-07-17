package com.kita.workflow.actor;

import com.kita.workflow.activity.ActivityOutcome;
import com.kita.workflow.activity.ActivityRecorder;
import com.kita.workflow.authorization.ActionAuthorizer;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.DownstreamUnavailableException;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.CallerContext;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The single entry point every controller uses: resolve the acting employee (FR-001), authorize the
 * action against their HR roles (FR-002), run the work, and record the terminal outcome to the
 * append-only log (FR-003) — success or any rejection, always with no side effect on a rejection.
 */
@Component
public class BackOfficePipeline {

  private final CallerContext caller;
  private final ActorResolver actorResolver;
  private final ActionAuthorizer authorizer;
  private final ActivityRecorder recorder;

  public BackOfficePipeline(
      CallerContext caller,
      ActorResolver actorResolver,
      ActionAuthorizer authorizer,
      ActivityRecorder recorder) {
    this.caller = caller;
    this.actorResolver = actorResolver;
    this.recorder = recorder;
    this.authorizer = authorizer;
  }

  /**
   * @param knownTargetRef target ref known up front (e.g. an existing order); may be null for creates
   * @param makerEmployeeId the maker on a checker's confirmation; else null
   * @param work the domain orchestration, given the resolved actor
   * @param successTargetRef derives the target ref from the result (for creates); may be null
   */
  public <T> T execute(
      BackOfficeAction action,
      AuthorizationKind kind,
      String knownTargetRef,
      String makerEmployeeId,
      Function<ResolvedActor, T> work,
      Function<T, String> successTargetRef) {

    String actor = caller.actor();

    ResolvedActor resolved;
    try {
      resolved = actorResolver.resolve(actor);
      authorizer.authorize(resolved.roles(), action, kind);
    } catch (ForbiddenException e) {
      recorder.record(
          actor, action, ActivityOutcome.REJECTED_NOT_PERMITTED, e.getMessage(),
          knownTargetRef, makerEmployeeId, null, 0);
      throw e;
    } catch (ValidationException e) {
      recorder.record(
          actor, action, ActivityOutcome.REJECTED_INVALID, e.getMessage(),
          knownTargetRef, makerEmployeeId, null, 0);
      throw e;
    }

    try {
      T result = work.apply(resolved);
      String targetRef = successTargetRef != null ? successTargetRef.apply(result) : knownTargetRef;
      recorder.record(
          actor, action, ActivityOutcome.SUCCESS, null, targetRef, makerEmployeeId, null, 0);
      return result;
    } catch (ValidationException e) {
      recorder.record(
          actor, action, ActivityOutcome.REJECTED_INVALID, e.getMessage(),
          knownTargetRef, makerEmployeeId, null, 0);
      throw e;
    } catch (DownstreamUnavailableException e) {
      recorder.record(
          actor, action, ActivityOutcome.FAILED_UNAVAILABLE, e.getMessage(),
          knownTargetRef, makerEmployeeId, null, 0);
      throw e;
    }
  }
}
