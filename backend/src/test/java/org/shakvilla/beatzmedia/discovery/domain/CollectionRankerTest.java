package org.shakvilla.beatzmedia.discovery.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Chart;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Metrics;
import org.shakvilla.beatzmedia.discovery.domain.CollectionRanker.Policy;

class CollectionRankerTest {

  @Test
  void topAndTrendingMeasureDifferentThings() {
    Metrics established = metrics("album:established", 1_000, 15, 700, 300, 8, 150, 80, 30, 20, 2_000, 10_000, 365);
    Metrics breakout = metrics("ep:breakout", 120, 80, 35, 70, 45, 50, 25, 12, 4, 100, 300, 7);

    assertEquals("album:established", CollectionRanker.rank(Chart.TOP, List.of(breakout, established), Policy.initial()).getFirst().contentKey());
    assertEquals("ep:breakout", CollectionRanker.rank(Chart.TRENDING, List.of(breakout, established), Policy.initial()).getFirst().contentKey());
    assertEquals(List.of("ep:breakout"), CollectionRanker.rank(Chart.EMERGING, List.of(breakout, established), Policy.initial()).stream().map(CollectionRanker.Ranked::contentKey).toList());
  }

  @Test
  void thresholdAndStableTieBreakPreventReplayDominance() {
    Metrics oneListener = metrics("single:replay", 10_000, 500, 10, 1, 1, 0, 0, 0, 0, 1, 1, 1);
    Metrics b = metrics("single:b", 10, 4, 7, 6, 3, 0, 0, 0, 0, 6, 6, 1);
    Metrics a = metrics("single:a", 10, 4, 7, 6, 3, 0, 0, 0, 0, 6, 6, 1);

    assertEquals(List.of("single:a", "single:b"), CollectionRanker.rank(Chart.TOP, List.of(b, oneListener, a), Policy.initial()).stream().map(CollectionRanker.Ranked::contentKey).toList());
    assertEquals(List.of("single:a", "single:b"), CollectionRanker.rank(Chart.TRENDING, List.of(b, oneListener, a), Policy.initial()).stream().map(CollectionRanker.Ranked::contentKey).toList());
  }

  @Test
  void rejectsNegativeInput() {
    assertThrows(IllegalArgumentException.class, () -> metrics("single:bad", -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
  }

  private static Metrics metrics(String key, long plays, long recent, long baseline, long listeners,
      long recentListeners, long completions, long saves, long follows, long conversions,
      long lifetime, long creatorListeners, int ageDays) {
    return new Metrics(key, plays, recent, baseline, listeners, recentListeners, completions,
        saves, follows, conversions, lifetime, creatorListeners, ageDays);
  }
}
