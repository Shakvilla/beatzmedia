package org.shakvilla.beatzmedia.admin.fakes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.shakvilla.beatzmedia.admin.application.port.out.AnalyticsAdminReader;
import org.shakvilla.beatzmedia.analytics.domain.Grain;

/**
 * In-memory fake for {@link AnalyticsAdminReader}. Callers seed a fixed {@link
 * AnalyticsAdminReader.Summary} per exact {@code (from, to, grain)} key. Testing-strategy §2.
 */
public class FakeAnalyticsAdminReader implements AnalyticsAdminReader {

  private final Map<String, Summary> summaries = new HashMap<>();

  public void seed(LocalDate from, LocalDate to, Grain grain, Summary summary) {
    summaries.put(key(from, to, grain), summary);
  }

  @Override
  public Summary salesSummary(LocalDate from, LocalDate to, Grain grain) {
    return summaries.getOrDefault(
        key(from, to, grain), new Summary(0L, 0L, List.of(), List.of()));
  }

  private static String key(LocalDate from, LocalDate to, Grain grain) {
    return from + "|" + to + "|" + grain;
  }
}
