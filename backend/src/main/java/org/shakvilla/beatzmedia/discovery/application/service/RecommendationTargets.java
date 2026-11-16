package org.shakvilla.beatzmedia.discovery.application.service;

import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;

final class RecommendationTargets {

  private RecommendationTargets() {}

  static String content(String kind, String id) {
    return "content:" + kind + ":" + id;
  }

  static String source(String sourceType, String id) {
    return sourceType + ":" + id;
  }

  static String content(RecommendationCandidate candidate) {
    return content(candidate.kind().wireValue(), candidate.contentId());
  }

  static String source(RecommendationCandidate candidate) {
    return source(sourceType(candidate), candidate.creatorId());
  }

  static String sourceType(RecommendationCandidate candidate) {
    return candidate.kind().wireValue().equals("podcast") ? "podcast" : "artist";
  }
}
