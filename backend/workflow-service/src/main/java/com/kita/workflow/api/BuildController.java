package com.kita.workflow.api;

import com.kita.workflow.actor.BackOfficePipeline;
import com.kita.workflow.api.dto.BuildDtos.BuildRequest;
import com.kita.workflow.api.dto.BuildDtos.BuildResponse;
import com.kita.workflow.authorization.AuthorizationKind;
import com.kita.workflow.authorization.BackOfficeAction;
import com.kita.workflow.workflow.BuildWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Production build endpoint (US5), run through the {@link BackOfficePipeline}. */
@RestController
@RequestMapping("/api/workflow/builds")
public class BuildController {

  private final BackOfficePipeline pipeline;
  private final BuildWorkflow workflow;

  public BuildController(BackOfficePipeline pipeline, BuildWorkflow workflow) {
    this.pipeline = pipeline;
    this.workflow = workflow;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BuildResponse build(@Valid @RequestBody BuildRequest body) {
    return pipeline.execute(
        BackOfficeAction.BUILD_PRODUCT,
        AuthorizationKind.PERFORM,
        "item:" + body.itemId(),
        null,
        actor -> {
          var result =
              workflow.build(
                  actor.employeeId(),
                  new BuildWorkflow.BuildRequest(body.itemId(), body.quantity()));
          return new BuildResponse(result.buildId(), result.produced());
        },
        r -> "build:" + r.buildId());
  }
}
