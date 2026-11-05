package org.shakvilla.beatzmedia.discovery.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.shakvilla.beatzmedia.discovery.application.port.in.GetPersonalizedRecommendations.RecommendationItem;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals.Snapshot;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationKind;

final class RecommendationRanker {

  private RecommendationRanker() {}

  static List<RecommendationItem> rank(
      List<RecommendationCandidate> candidates, Snapshot signals, int limit, Instant now) {
    Map<String, Ranked> unique = new HashMap<>();
    for (RecommendationCandidate candidate : candidates) {
      if (excluded(candidate, signals)) continue;
      Ranked ranked = score(candidate, signals, now);
      unique.merge(
          candidate.key(),
          ranked,
          (left, right) -> ORDER.compare(left, right) <= 0 ? left : right);
    }

    List<Ranked> ordered = unique.values().stream().sorted(ORDER).toList();
    if (ordered.size() <= limit) return ordered.stream().map(RecommendationRanker::view).toList();

    Set<Ranked> selected = new LinkedHashSet<>();
    for (RecommendationKind kind : RecommendationKind.values()) {
      if (selected.size() == limit) break;
      firstOfKind(ordered, kind).ifPresent(selected::add);
    }
    for (Ranked ranked : ordered) {
      if (selected.size() == limit) break;
      selected.add(ranked);
    }
    return selected.stream().sorted(ORDER).map(RecommendationRanker::view).toList();
  }

  private static boolean excluded(RecommendationCandidate candidate, Snapshot signals) {
    boolean ownedSingle =
        candidate.kind() == RecommendationKind.single
            && signals.excludedTrackIds().contains(candidate.contentId());
    boolean fullyOwnedRelease =
        (candidate.kind() == RecommendationKind.album || candidate.kind() == RecommendationKind.ep)
            && !candidate.memberTrackIds().isEmpty()
            && signals.excludedTrackIds().containsAll(candidate.memberTrackIds());
    return ownedSingle
        || fullyOwnedRelease
        || signals.completedContentKeys().contains(candidate.historyKey());
  }

  private static Ranked score(
      RecommendationCandidate candidate, Snapshot signals, Instant now) {
    int score = 0;
    String reason = "Popular on BeatzClik";
    boolean followed =
        candidate.kind() == RecommendationKind.podcast
            ? signals.followedShowIds().contains(candidate.creatorId())
            : signals.followedArtistIds().contains(candidate.creatorId());
    if (followed) {
      score += 100;
      reason = "Because you follow " + candidate.creatorName();
    } else if (signals.preferredCreatorIds().contains(candidate.creatorId())) {
      score += 45;
      reason = "More from " + candidate.creatorName();
    } else {
      String matchingTag = matchingTag(candidate.tags(), signals.preferredTags());
      if (matchingTag != null) {
        score += 25;
        reason = "Based on your " + matchingTag + " listening";
      }
    }
    if (signals.historyEnabled()
        && signals.recentContentKeys().contains(candidate.historyKey())) score += 12;
    score += Math.min(20, (int) Math.round(Math.log10(Math.max(0, candidate.popularity()) + 1) * 4));
    if (candidate.publishedAt() != null) {
      long ageDays = Math.max(0, Duration.between(candidate.publishedAt(), now).toDays());
      if (ageDays <= 30) score += 8;
      else if (ageDays <= 90) score += 4;
    }
    return new Ranked(candidate, score, reason);
  }

  private static String matchingTag(List<String> tags, Set<String> preferredTags) {
    for (String tag : tags) {
      if (preferredTags.contains(normalize(tag))) return tag;
    }
    return null;
  }

  static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private static java.util.Optional<Ranked> firstOfKind(
      List<Ranked> ranked, RecommendationKind kind) {
    return ranked.stream().filter(item -> item.candidate().kind() == kind).findFirst();
  }

  private static RecommendationItem view(Ranked ranked) {
    var item = ranked.candidate();
    return new RecommendationItem(
        item.kind().wireValue(),
        item.contentId(),
        item.creatorId(),
        item.title(),
        item.subtitle(),
        item.image(),
        item.route(),
        ranked.reason());
  }

  private static final Comparator<Ranked> ORDER =
      Comparator.comparingInt(Ranked::score)
          .reversed()
          .thenComparing(ranked -> ranked.candidate().kind().wireValue())
          .thenComparing(ranked -> ranked.candidate().contentId());

  private record Ranked(RecommendationCandidate candidate, int score, String reason) {}
}
