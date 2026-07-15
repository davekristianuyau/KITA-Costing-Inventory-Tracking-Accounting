package com.kita.procurement.restock.dto;

import com.kita.procurement.restock.RestockStatus;
import com.kita.procurement.restock.RestockSuggestion;
import com.kita.procurement.restock.RestockSuggestionLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RestockSuggestionResponse(
    UUID id,
    UUID supplierId,
    RestockStatus status,
    Instant generatedAt,
    UUID convertedPoId,
    List<LineResponse> lines) {

  public record LineResponse(
      String itemRef,
      BigDecimal suggestedQty,
      BigDecimal onHand,
      BigDecimal targetLevel,
      String reason) {

    static LineResponse from(RestockSuggestionLine l) {
      return new LineResponse(
          l.getItemRef(), l.getSuggestedQty(), l.getOnHand(), l.getTargetLevel(), l.getReason());
    }
  }

  public static RestockSuggestionResponse from(RestockSuggestion s, List<RestockSuggestionLine> lines) {
    return new RestockSuggestionResponse(
        s.getId(),
        s.getSupplierId(),
        s.getStatus(),
        s.getGeneratedAt(),
        s.getConvertedPoId(),
        lines.stream().map(LineResponse::from).toList());
  }
}
