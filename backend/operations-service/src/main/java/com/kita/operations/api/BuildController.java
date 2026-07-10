package com.kita.operations.api;

import com.kita.operations.api.BuildDtos.BuildRequest;
import com.kita.operations.api.BuildDtos.BuildResponse;
import com.kita.operations.production.Build;
import com.kita.operations.production.BuildService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/builds")
public class BuildController {

  private final BuildService builds;

  public BuildController(BuildService builds) {
    this.builds = builds;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BuildResponse build(@Valid @RequestBody BuildRequest req) {
    Build b = builds.build(req.finishedItemId(), req.locationId(), req.quantity());
    return new BuildResponse(b.getId(), b.getFinishedItem().getId(), b.getQuantity(), b.getStatus().name());
  }
}
