package com.kita.operations.api;

import com.kita.operations.api.BuildDtos.BuildRequest;
import com.kita.operations.api.BuildDtos.BuildResponse;
import com.kita.operations.production.Build;
import com.kita.operations.production.BuildService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @GetMapping
  public List<BuildResponse> list() {
    return builds.list().stream().map(BuildController::toResponse).toList();
  }

  @GetMapping("/{id}")
  public BuildResponse getOne(@PathVariable UUID id) {
    return toResponse(builds.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BuildResponse build(@Valid @RequestBody BuildRequest req) {
    return toResponse(builds.build(req.finishedItemId(), req.locationId(), req.quantity()));
  }

  private static BuildResponse toResponse(Build b) {
    return new BuildResponse(
        b.getId(), b.getFinishedItem().getId(), b.getQuantity(), b.getStatus().name());
  }
}
