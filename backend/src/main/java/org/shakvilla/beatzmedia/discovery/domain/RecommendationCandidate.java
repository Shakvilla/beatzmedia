package org.shakvilla.beatzmedia.discovery.domain;

import java.time.Instant;
import java.util.List;

public record RecommendationCandidate(
    RecommendationKind kind,
    String contentId,
    String creatorId,
    String creatorName,
    String title,
    String subtitle,
    String image,
    String route,
    List<String> tags,
    List<String> memberTrackIds,
    long popularity,
    Instant publishedAt) {

  public RecommendationCandidate {
    tags = tags == null ? List.of() : List.copyOf(tags);
    memberTrackIds = memberTrackIds == null ? List.of() : List.copyOf(memberTrackIds);
  }

  public String key() {
    return kind.wireValue() + ":" + contentId;
  }

  public String historyKey() {
    return kind == RecommendationKind.single ? "track:" + contentId : key();
  }
}
