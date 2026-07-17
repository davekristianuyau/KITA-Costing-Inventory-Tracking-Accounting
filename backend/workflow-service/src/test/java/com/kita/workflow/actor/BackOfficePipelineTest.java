package com.kita.workflow.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kita.workflow.activity.ActivityOutcome;
import com.kita.workflow.activity.ActivityRecorder;
import com.kita.workflow.authorization.ActionAuthorizer;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.AuthorizationRule;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.common.ForbiddenException;
import com.kita.workflow.common.ValidationException;
import com.kita.workflow.common.security.CallerContext;
import com.kita.workflow.common.security.Role;
import com.kita.workflow.ports.fake.InMemoryHrAdapter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Verifies the pipeline: resolve → authorize → run → record, with no side effect on rejection. */
class BackOfficePipelineTest {

  private final CallerContext caller = mock(CallerContext.class);
  private final ActivityRecorder recorder = mock(ActivityRecorder.class);
  private final ActorResolver actorResolver = new ActorResolver(new InMemoryHrAdapter(false));
  private final ActionAuthorizer authorizer =
      new ActionAuthorizer(
          List.of(
              new AuthorizationRule(
                  BackOfficeAction.CONFIRM_SALES_PAYMENT,
                  Role.CASHIER,
                  AuthorizationKind.CHECKER)));
  private final BackOfficePipeline pipeline =
      new BackOfficePipeline(caller, actorResolver, authorizer, recorder);

  @Test
  void authorizedActionRunsAndRecordsSuccess() {
    when(caller.actor()).thenReturn("emp-cashier");
    AtomicBoolean ran = new AtomicBoolean();
    String result =
        pipeline.execute(
            BackOfficeAction.CONFIRM_SALES_PAYMENT,
            AuthorizationKind.CHECKER,
            "sales-order:1",
            null,
            actor -> {
              ran.set(true);
              return "PAYMENT_CONFIRMED";
            },
            null);
    assertThat(result).isEqualTo("PAYMENT_CONFIRMED");
    assertThat(ran).isTrue();
    verify(recorder)
        .record(
            eq("emp-cashier"),
            eq(BackOfficeAction.CONFIRM_SALES_PAYMENT),
            eq(ActivityOutcome.SUCCESS),
            any(),
            any(),
            any(),
            any(),
            anyInt());
  }

  @Test
  void unauthorizedActionIsRejectedWithNoWork() {
    when(caller.actor()).thenReturn("emp-sales"); // SALES lacks the CHECKER grant
    AtomicBoolean ran = new AtomicBoolean();
    assertThatThrownBy(
            () ->
                pipeline.execute(
                    BackOfficeAction.CONFIRM_SALES_PAYMENT,
                    AuthorizationKind.CHECKER,
                    "sales-order:1",
                    null,
                    actor -> {
                      ran.set(true);
                      return "x";
                    },
                    null))
        .isInstanceOf(ForbiddenException.class);
    assertThat(ran).isFalse();
    verify(recorder)
        .record(
            eq("emp-sales"),
            eq(BackOfficeAction.CONFIRM_SALES_PAYMENT),
            eq(ActivityOutcome.REJECTED_NOT_PERMITTED),
            any(),
            any(),
            any(),
            any(),
            anyInt());
  }

  @Test
  void unknownActorIsRejectedInvalidWithNoWork() {
    when(caller.actor()).thenReturn("nobody");
    AtomicBoolean ran = new AtomicBoolean();
    assertThatThrownBy(
            () ->
                pipeline.execute(
                    BackOfficeAction.CONFIRM_SALES_PAYMENT,
                    AuthorizationKind.CHECKER,
                    "sales-order:1",
                    null,
                    actor -> {
                      ran.set(true);
                      return "x";
                    },
                    null))
        .isInstanceOf(ValidationException.class);
    assertThat(ran).isFalse();
    verify(recorder)
        .record(
            eq("nobody"),
            eq(BackOfficeAction.CONFIRM_SALES_PAYMENT),
            eq(ActivityOutcome.REJECTED_INVALID),
            any(),
            any(),
            any(),
            any(),
            anyInt());
  }

  @Test
  void separatedActorIsRejectedInvalid() {
    when(caller.actor()).thenReturn("emp-separated");
    assertThatThrownBy(
            () ->
                pipeline.execute(
                    BackOfficeAction.CONFIRM_SALES_PAYMENT,
                    AuthorizationKind.CHECKER,
                    "sales-order:1",
                    null,
                    actor -> "x",
                    null))
        .isInstanceOf(ValidationException.class);
  }
}
