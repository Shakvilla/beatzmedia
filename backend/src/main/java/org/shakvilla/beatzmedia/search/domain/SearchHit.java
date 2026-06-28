package org.shakvilla.beatzmedia.search.domain;

import java.util.Map;
import java.util.Objects;

/**
 * One ranked match returned by the search port (ADD §3).
 * {@code score} is ts_rank_cd output; {@code payload} is the denormalised JSON from the index row.
 */
public record SearchHit(
    EntityType entityType,
    String entityId,
    String title,
    String subtitle,
    Map<String, Object> payload,
    double score,
    long popularity) {

  public SearchHit {
    Objects.requireNonNull(entityType, "entityType");
    Objects.requireNonNull(entityId, "entityId");
    Objects.requireNonNull(title, "title");
    payload = payload == null ? Map.of() : Map.copyOf(payload);
  }
}
