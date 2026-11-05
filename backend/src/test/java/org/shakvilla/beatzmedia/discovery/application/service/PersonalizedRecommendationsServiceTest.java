package org.shakvilla.beatzmedia.discovery.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.application.port.out.PersonalizationSignals;
import org.shakvilla.beatzmedia.discovery.application.port.out.RecommendationContent;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationCandidate;
import org.shakvilla.beatzmedia.discovery.domain.RecommendationKind;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;

@Tag("unit")
class PersonalizedRecommendationsServiceTest {

  @Test
  void clampsThePublicLimitToTwentyFour() {
    List<RecommendationCandidate> candidates = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      candidates.add(
          new RecommendationCandidate(
              RecommendationKind.single,
              "track-" + i,
              "artist-" + i,
              "Artist",
              "Track",
              "Artist",
              null,
              "/track/track-" + i,
              List.of(),
              List.of("track-" + i),
              i,
              null));
    }
    PersonalizationSignals signals = accountId -> PersonalizationSignals.Snapshot.empty();
    RecommendationContent content = (accountId, snapshot) -> candidates;
    var service =
        new PersonalizedRecommendationsService(
            signals, content, FakeClock.at("2026-11-04T12:00:00Z"));

    assertEquals(24, service.get("account-1", 500).items().size());
  }
}
