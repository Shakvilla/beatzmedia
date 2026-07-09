package org.shakvilla.beatzmedia.studio.fakes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader;
import org.shakvilla.beatzmedia.studio.domain.AnalyticsRange;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;

/**
 * In-memory fake for {@link AnalyticsReader} used in unit tests. Records the last {@code (artist,
 * range)} it was called with so tests can assert the caller's own artist id — never a foreign one
 * — is what reaches the output port (application-layer half of the IDOR guarantee).
 */
public class FakeAnalyticsReader implements AnalyticsReader {

  private final Map<String, Insights> byArtist = new HashMap<>();
  private Insights defaultInsights = zero();

  public ArtistId lastArtist;
  public AnalyticsRange lastRange;

  public FakeAnalyticsReader withInsightsFor(ArtistId artist, Insights insights) {
    byArtist.put(artist.value(), insights);
    return this;
  }

  public FakeAnalyticsReader withDefaultInsights(Insights insights) {
    this.defaultInsights = insights;
    return this;
  }

  @Override
  public Insights readInsights(ArtistId artist, AnalyticsRange range) {
    this.lastArtist = artist;
    this.lastRange = range;
    return byArtist.getOrDefault(artist.value(), defaultInsights);
  }

  public static Insights zero() {
    Map<String, MetricSeriesData> metrics = new HashMap<>();
    metrics.put("streams", zeroSeries());
    metrics.put("sales", zeroSeries());
    metrics.put("followers", zeroSeries());
    metrics.put("tips", zeroSeries());
    return new Insights("Last 28 days", "DAILY", List.of(), metrics, 0L, 0L, 0L);
  }

  private static MetricSeriesData zeroSeries() {
    return new MetricSeriesData(0L, 0, List.of(), List.of());
  }
}
