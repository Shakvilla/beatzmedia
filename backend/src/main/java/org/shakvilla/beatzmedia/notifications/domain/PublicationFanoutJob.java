package org.shakvilla.beatzmedia.notifications.domain;

import java.time.Instant;

/** Durable snapshot and progress cursor for one publication's follower notification fanout. */
public record PublicationFanoutJob(
    String id,
    PublicationKind publicationKind,
    String contentId,
    String targetKind,
    String targetId,
    String title,
    String body,
    String route,
    String cursor,
    FanoutStatus status,
    int retryCount,
    Instant nextAttemptAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt) {

  public PublicationFanoutJob {
    requireText(id, "id");
    if (publicationKind == null) {
      throw new IllegalArgumentException("publicationKind must not be null");
    }
    requireText(contentId, "contentId");
    if (!"artist".equals(targetKind) && !"show".equals(targetKind)) {
      throw new IllegalArgumentException("targetKind must be artist or show");
    }
    requireText(targetId, "targetId");
    requireText(title, "title");
    requireText(body, "body");
    requireText(route, "route");
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (retryCount < 0) {
      throw new IllegalArgumentException("retryCount must not be negative");
    }
    if (nextAttemptAt == null || createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("fanout timestamps must not be null");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
