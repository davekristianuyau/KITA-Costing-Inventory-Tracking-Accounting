package com.kita.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T056: statutory/tax identifiers must never reach logs or audit detail in the clear (FR-004/023). */
class LogScrubberTest {

  @Test
  void maskKeepsOnlyTheLastFourCharacters() {
    assertThat(LogScrubber.mask("03-1234567-8")).isEqualTo("********67-8");
    assertThat(LogScrubber.mask("123456789012")).isEqualTo("********9012");
  }

  @Test
  void maskHidesShortValuesEntirely() {
    assertThat(LogScrubber.mask("1234")).isEqualTo("****");
    assertThat(LogScrubber.mask("12")).isEqualTo("**");
  }

  @Test
  void maskHandlesNullAndBlank() {
    assertThat(LogScrubber.mask(null)).isNull();
    assertThat(LogScrubber.mask("")).isEqualTo("");
  }

  @Test
  void scrubMasksStatutoryIdsWrittenAsKeyValuePairs() {
    String scrubbed = LogScrubber.scrub("employee sssNo=03-1234567-8 tin=123-456-789 saved");
    assertThat(scrubbed).doesNotContain("03-1234567-8").doesNotContain("123-456-789");
    assertThat(scrubbed).contains("sssNo=").contains("tin=");
  }

  @Test
  void scrubMasksStatutoryIdsInJsonStyleText() {
    String scrubbed = LogScrubber.scrub("{\"tin\":\"123-456-789-000\",\"philhealthNo\":\"12-345678901-2\"}");
    assertThat(scrubbed).doesNotContain("123-456-789-000").doesNotContain("12-345678901-2");
  }

  @Test
  void scrubLeavesNonSensitiveTextAlone() {
    String text = "run=REGULAR:2026-01-01:2026-01-31 payslips=12 gross=30000.00";
    assertThat(LogScrubber.scrub(text)).isEqualTo(text);
  }

  @Test
  void scrubDoesNotMangleUuidsOrEmployeeNumbers() {
    String text = "entity=3f2504e0-4f89-11d3-9a0c-0305e82c3301 employeeNo=E-001";
    assertThat(LogScrubber.scrub(text)).isEqualTo(text);
  }

  @Test
  void scrubHandlesNull() {
    assertThat(LogScrubber.scrub(null)).isNull();
  }
}
