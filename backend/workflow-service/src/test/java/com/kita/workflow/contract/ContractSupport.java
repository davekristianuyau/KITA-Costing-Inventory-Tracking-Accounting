package com.kita.workflow.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consumer-contract test support (US2, FR-005/006): verify a workflow adapter's outbound request body
 * against the <em>receiving service's real DTO record</em>. The receiver modules are on the test
 * classpath (see build.gradle.kts), so a field renamed / removed / re-typed on <em>either</em> side —
 * the adapter's body or the receiver's record — makes {@link #bindAndValidate} fail, turning drift into
 * a red build (SC-003) instead of a runtime rejection.
 *
 * <p>The mapper mirrors the app's on-the-wire behaviour: unknown properties are rejected so a body with
 * a stale field name (e.g. {@code customerId} when the receiver now expects {@code customerRef}) fails
 * binding rather than being silently dropped.
 */
public final class ContractSupport {

  private ContractSupport() {}

  /** Rejects unknown fields — the whole point is to catch a body key the receiver does not accept. */
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  /** Serialise the adapter body, bind it to the receiver's real DTO type. Throws on any mismatch. */
  public static <T> T bind(Object requestBody, Class<T> receiverDto) {
    try {
      String json = MAPPER.writeValueAsString(requestBody);
      return MAPPER.readValue(json, receiverDto);
    } catch (Exception e) {
      throw new AssertionError(
          "request body does not bind to " + receiverDto.getSimpleName() + ": " + e.getMessage(), e);
    }
  }

  /** Bind, then assert the bound DTO satisfies the receiver's Bean-Validation constraints. */
  public static <T> T bindAndValidate(Object requestBody, Class<T> receiverDto) {
    T dto = bind(requestBody, receiverDto);
    Set<ConstraintViolation<T>> violations = VALIDATOR.validate(dto);
    assertThat(violations)
        .withFailMessage(
            () ->
                "body violates "
                    + receiverDto.getSimpleName()
                    + " constraints: "
                    + violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining(", ")))
        .isEmpty();
    return dto;
  }
}
