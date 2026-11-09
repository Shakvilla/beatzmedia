package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** A versioned event appended atomically with an aggregate state transition. */
public record DurableEventMessage(
    String id,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    Map<String, String> payload,
    Instant occurredAt) {

  public DurableEventMessage {
    id = requireText(id, "id");
    eventType = requireText(eventType, "eventType");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
    aggregateType = requireText(aggregateType, "aggregateType");
    aggregateId = requireText(aggregateId, "aggregateId");
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
