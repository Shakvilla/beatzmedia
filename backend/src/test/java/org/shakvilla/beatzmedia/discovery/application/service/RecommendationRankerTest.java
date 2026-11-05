package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals.Snapshot;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationKind;

@Tag("unit")
class RecommendationRankerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

  @Test
  void directFollowsWinAndExposeAnExplainableReason() {
    var signals =
        snapshot(Set.of("artist-followed"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    var popular = track("popular", "artist-popular", "Popular Artist", 1_000_000, List.of());
    var followed = track("followed", "artist-followed", "Ama", 5, List.of());

    var result = RecommendationRanker.rank(List.of(popular, followed), signals, 12, NOW);

    assertEquals("followed", result.getFirst().contentId());
    assertEquals("Because you follow Ama", result.getFirst().reason());
  }

  @Test
  void excludesOwnedSinglesFullyOwnedReleasesAndCompletedContentAndDeduplicatesCandidates() {
    var signals =
        snapshot(
            Set.of(),
            Set.of(),
            Set.of("owned", "album-track-1", "album-track-2"),
            Set.of("track:done"),
            Set.of(),
            Set.of());
    var duplicate = track("fresh", "artist-1", "Artist", 10, List.of());
    var result =
        RecommendationRanker.rank(
            List.of(
                track("owned", "artist-1", "Artist", 100, List.of()),
                track("done", "artist-1", "Artist", 100, List.of()),
                release(
                    RecommendationKind.album,
                    "owned-album",
                    "artist-1",
                    List.of("album-track-1", "album-track-2")),
                duplicate,
                duplicate),
            signals,
            12,
            NOW);

    assertEquals(List.of("fresh"), result.stream().map(item -> item.contentId()).toList());
  }

  @Test
  void reservesSpaceForEveryAvailableContentType() {
    var candidates = new java.util.ArrayList<RecommendationCandidate>();
    for (int i = 0; i < 8; i++) {
      candidates.add(track("track-" + i, "artist-" + i, "Artist", 1000 - i, List.of()));
    }
    candidates.add(release(RecommendationKind.album, "album-1", "artist-a", List.of("a1")));
    candidates.add(release(RecommendationKind.ep, "ep-1", "artist-e", List.of("e1")));
    candidates.add(podcast("show-1", "Show", 1));

    var result = RecommendationRanker.rank(candidates, Snapshot.empty(), 4, NOW);

    assertEquals(
        Set.of("single", "album", "ep", "podcast"),
        result.stream().map(item -> item.kind()).collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void usesAffinityReasonButHistoryOptOutRemovesRecentBoost() {
    var optedOut =
        snapshot(Set.of(), Set.of(), Set.of(), Set.of(), Set.of("afrobeats"), Set.of("track:recent"));
    var result =
        RecommendationRanker.rank(
            List.of(
                track("recent", "artist-1", "Artist", 0, List.of()),
                track("genre", "artist-2", "Artist", 0, List.of("Afrobeats"))),
            optedOut,
            12,
            NOW);

    assertEquals("genre", result.getFirst().contentId());
    assertEquals("Based on your Afrobeats listening", result.getFirst().reason());
    assertFalse(optedOut.historyEnabled());
  }

  private static Snapshot snapshot(
      Set<String> followedArtists,
      Set<String> followedShows,
      Set<String> excludedTracks,
      Set<String> completed,
      Set<String> preferredTags,
      Set<String> recent) {
    return new Snapshot(
        followedArtists,
        followedShows,
        excludedTracks,
        completed,
        recent,
        followedArtists,
        preferredTags,
        false);
  }

  private static RecommendationCandidate track(
      String id, String creatorId, String creatorName, long popularity, List<String> tags) {
    return new RecommendationCandidate(
        RecommendationKind.single,
        id,
        creatorId,
        creatorName,
        "Track " + id,
        creatorName,
        "/image",
        "/track/" + id,
        tags,
        List.of(id),
        popularity,
        NOW.minusSeconds(86_400));
  }

  private static RecommendationCandidate release(
      RecommendationKind kind, String id, String creatorId, List<String> tracks) {
    return new RecommendationCandidate(
        kind,
        id,
        creatorId,
        "Artist",
        "Release " + id,
        "Artist",
        "/image",
        "/album/" + id,
        List.of("Afrobeats"),
        tracks,
        10,
        NOW.minusSeconds(86_400));
  }

  private static RecommendationCandidate podcast(
      String id, String creatorName, long popularity) {
    return new RecommendationCandidate(
        RecommendationKind.podcast,
        id,
        id,
        creatorName,
        creatorName,
        "Publisher",
        "/image",
        "/podcast/" + id,
        List.of("Culture"),
        List.of(),
        popularity,
        NOW.minusSeconds(86_400));
  }
}
