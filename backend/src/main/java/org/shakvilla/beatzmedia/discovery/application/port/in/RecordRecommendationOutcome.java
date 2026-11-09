package org.shakvilla.beatzmedia.discovery.application.port.in;

import java.time.Instant;

public interface RecordRecommendationOutcome {

  void record(
      String accountId,
      String recommendationId,
      String eventId,
      String eventType,
      String contentKind,
      String contentId,
      String parentId,
      Instant occurredAt);
}
