package org.shakvilla.beatzmedia.engagement.domain;

import java.time.Instant;

import org.shakvilla.beatzmedia.platform.domain.ValidationException;

public record ListeningCheckpoint(
    String id,
    String sessionId,
    String accountId,
    long sequence,
    ContentKind contentKind,
    String contentId,
    EngagementEventType eventType,
    int positionSeconds,
    int durationSeconds,
    int listenedDeltaSeconds,
    boolean effective,
    Instant receivedAt) {

  public ListeningCheckpoint {
    if (sequence <= 0) throw new ValidationException("sequence must be positive", "sequence");
    if (contentId == null || contentId.isBlank()) {
      throw new ValidationException("contentId must not be blank", "contentId");
    }
    if (positionSeconds < 0) {
      throw new ValidationException("positionSeconds must not be negative", "positionSeconds");
    }
    if (durationSeconds <= 0) {
      throw new ValidationException("durationSeconds must be positive", "durationSeconds");
    }
    if (listenedDeltaSeconds < 0) {
      throw new ValidationException(
          "listenedDeltaSeconds must not be negative", "listenedDeltaSeconds");
    }
    if (positionSeconds > durationSeconds + 2) {
      throw new ValidationException("positionSeconds exceeds duration", "positionSeconds");
    }
    if (listenedDeltaSeconds > durationSeconds) {
      throw new ValidationException("listenedDeltaSeconds exceeds duration", "listenedDeltaSeconds");
    }
  }

  public boolean samePayload(ListeningCheckpoint other) {
    return sequence == other.sequence
        && contentKind == other.contentKind
        && contentId.equals(other.contentId)
        && eventType == other.eventType
        && positionSeconds == other.positionSeconds
        && durationSeconds == other.durationSeconds
        && listenedDeltaSeconds == other.listenedDeltaSeconds;
  }

  public boolean completed() {
    return eventType == EngagementEventType.COMPLETION
        || (long) positionSeconds * 100L >= (long) durationSeconds * 95L;
  }
}
