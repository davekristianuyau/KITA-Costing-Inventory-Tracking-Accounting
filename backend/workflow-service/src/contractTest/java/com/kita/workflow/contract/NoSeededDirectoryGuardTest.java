package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 017 SC-005 / FR-012 / FR-014 — the stand-ins must stay gone.
 *
 * <p>Both were removed by hand, and nothing at runtime would notice them coming back: 018's
 * {@code workflow.hr.position-roles} map derived roles from a job title in deployment config, and the
 * demo relied on login names that happened to match seeded employee ids. Either would quietly re-create
 * a second source of roles beside the personnel record, which is precisely what FR-014 forbids.
 *
 * <p>A source-scanning guard is unusual, but the alternative is no guard at all — these are absences,
 * and absences cannot be asserted from behaviour.
 */
class NoSeededDirectoryGuardTest {

  /** Walk up to the repository root, so the test works from any module's working directory. */
  private static Path repoRoot() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null && !Files.exists(dir.resolve("backend").resolve("settings.gradle.kts"))) {
      dir = dir.getParent();
    }
    return dir;
  }

  private static List<String> scan(String... needles) throws IOException {
    Path root = repoRoot();
    if (root == null) {
      return List.of(); // not in a checkout (shaded/CI edge case) — nothing to assert
    }
    List<String> hits = new ArrayList<>();
    List<Path> roots =
        List.of(root.resolve("backend"), root.resolve("docker-compose.yml"), root.resolve("sim"));
    for (Path base : roots) {
      if (!Files.exists(base)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(base)) {
        files
            .filter(Files::isRegularFile)
            .filter(NoSeededDirectoryGuardTest::isScannable)
            .forEach(
                file -> {
                  try {
                    String body = Files.readString(file, StandardCharsets.UTF_8);
                    for (String needle : needles) {
                      if (body.contains(needle)) {
                        hits.add(root.relativize(file) + " contains '" + needle + "'");
                      }
                    }
                  } catch (IOException | RuntimeException ignored) {
                    // unreadable/binary file — nothing to assert about it
                  }
                });
      }
    }
    return hits;
  }

  private static boolean isScannable(Path p) {
    String s = p.toString().replace('\\', '/');
    if (s.contains("/build/") || s.contains("/.gradle/") || s.contains("/bin/")) {
      return false;
    }
    // This test names the forbidden strings, so it would match itself.
    if (s.endsWith("NoSeededDirectoryGuardTest.java")) {
      return false;
    }
    // The ONE sanctioned exception: InMemoryHrAdapter is the isolated-build seam, explicitly not a
    // deployed path (every stack sets HR_ADAPTER=http). It may invent an employee id for its fixtures.
    // Narrow on purpose — excluding anything else here would be silencing the guard, not scoping it.
    if (s.endsWith("ports/fake/InMemoryHrAdapter.java")) {
      return false;
    }
    return s.endsWith(".java") || s.endsWith(".yml") || s.endsWith(".yaml") || s.endsWith(".sh");
  }

  @Test
  void the018PositionRolesStandInIsGone() throws IOException {
    assertThat(scan("HrPositionRoles", "position-roles"))
        .as(
            "roles must come from the personnel record only (FR-014). A position→roles map is a second"
                + " source of truth, and one that needs a redeploy to change (breaking SC-001/SC-006).")
        .isEmpty();
  }

  @Test
  void noDeployedPathResolvesAnEmployeeFromASeededLoginName() throws IOException {
    // The old trick: name a login exactly like a seeded employee id so the two "match". After 017 the
    // link is explicit, so any code reconstructing an employee id from an account name is a regression.
    assertThat(scan("\"emp-\" + account", "emp-\" + accountUsername"))
        .as("employee ids must come from the link, never be derived from the login name (FR-012)")
        .isEmpty();
  }
}
