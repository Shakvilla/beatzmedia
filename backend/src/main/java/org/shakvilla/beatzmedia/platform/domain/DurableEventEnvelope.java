package org.shakvilla.beatzmedia.platform.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** A claimed durable event delivered at least once to a named consumer. */
public record DurableEventEnvelope(
    String id,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    Map<String, String> payload,
    Instant occurredAt,
    int attemptCount) {

  public DurableEventEnvelope {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    payload = Map.copyOf(Objects.requireNonNull(payload, "payload"));
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
