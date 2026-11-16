package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;

public record DurableEventFilter(
    DurableEventStatus status,
    String eventType,
    String aggregateId,
    Instant occurredFrom,
    Instant occurredTo,
    PageRequest page) {

  public DurableEventFilter {
    eventType = blankToNull(eventType);
    aggregateId = blankToNull(aggregateId);
    page = page == null ? PageRequest.defaults() : page;
    if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
      throw new ValidationException("occurredFrom must not be after occurredTo", "occurredFrom");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
