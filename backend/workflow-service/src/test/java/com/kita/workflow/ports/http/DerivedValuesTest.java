package com.kita.workflow.ports.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kita.workflow.common.ValidationException;
import com.kita.workflow.ports.http.DerivedValues.ItemOption;
import com.kita.workflow.ports.http.DerivedValues.LocationOption;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the deterministic derivations (FR-002): same input → same value, always. */
class DerivedValuesTest {

  @Test
  void supplierCodeIsDeterministicAndSlugged() {
    assertThat(DerivedValues.supplierCode("Acme Corp.")).isEqualTo("ACME-CORP");
    assertThat(DerivedValues.supplierCode("Acme Corp.")).isEqualTo(DerivedValues.supplierCode("Acme Corp."));
    assertThat(DerivedValues.supplierCode("  weird  //name!! ")).isEqualTo("WEIRD-NAME");
  }

  @Test
  void supplierCodeRejectsBlankName() {
    assertThatThrownBy(() -> DerivedValues.supplierCode("  "))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void resolveSkuIsCaseInsensitiveAndRejectsUnknown() {
    UUID id = UUID.randomUUID();
    List<ItemOption> catalog = List.of(new ItemOption("WIDGET", id), new ItemOption("GADGET", UUID.randomUUID()));
    assertThat(DerivedValues.resolveSku("widget", catalog)).isEqualTo(id);
    assertThatThrownBy(() -> DerivedValues.resolveSku("nope", catalog))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void defaultLocationPrefersMainThenLowestCode() {
    UUID main = UUID.randomUUID();
    UUID a = UUID.randomUUID();
    assertThat(
            DerivedValues.defaultLocation(
                List.of(new LocationOption("ZONE-B", UUID.randomUUID()), new LocationOption("MAIN", main))))
        .isEqualTo(main);
    // no preferred code → lowest alphabetically
    assertThat(
            DerivedValues.defaultLocation(
                List.of(new LocationOption("ZONE-B", UUID.randomUUID()), new LocationOption("AISLE-1", a))))
        .isEqualTo(a);
    assertThatThrownBy(() -> DerivedValues.defaultLocation(List.of()))
        .isInstanceOf(ValidationException.class);
  }
}
