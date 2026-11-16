package org.shakvilla.beatzmedia.discovery.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.shakvilla.beatzmedia.discovery.domain.FeedKind;
import org.shakvilla.beatzmedia.discovery.domain.FollowingFeedItem;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

final class FollowingCursor {
  private FollowingCursor() {}

  static String encode(FollowingFeedItem item) {
    String raw = item.publishedAt().toEpochMilli() + "|" + item.kind().name() + "|" + item.contentId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  static Decoded decode(String cursor) {
    if (cursor == null || cursor.isBlank()) return new Decoded(null, null, null);
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|", 3);
      if (parts.length != 3) throw new IllegalArgumentException();
      return new Decoded(
          Instant.ofEpochMilli(Long.parseLong(parts[0])),
          FeedKind.valueOf(parts[1]),
          parts[2]);
    } catch (RuntimeException invalid) {
      throw new ValidationException("Invalid following-feed cursor", "cursor");
    }
  }

  record Decoded(Instant publishedAt, FeedKind kind, String contentId) {}
}
