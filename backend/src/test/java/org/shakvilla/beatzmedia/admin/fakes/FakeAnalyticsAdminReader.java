package org.shakvilla.beatzmedia.admin.fakes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.shakvilla.beatzmedia.admin.application.port.out.AnalyticsAdminReader;
import org.shakvilla.beatzmedia.analytics.domain.Grain;

/**
 * In-memory fake for {@link AnalyticsAdminReader}. Callers seed a fixed {@link
 * AnalyticsAdminReader.Summary} per exact {@code (from, to, grain)} key. Testing-strategy §2.
 *
 * <p>The unseeded default mirrors {@code analytics.GetPlatformSalesSummaryService}'s real
 * contract: {@code salesByBucket} is always zero-padded to one entry per bucket in {@code [from,
 * to]}, never an empty list, even with zero facts (admin ADD §13 as-built).
 */
public class FakeAnalyticsAdminReader implements AnalyticsAdminReader {

  private final Map<String, Summary> summaries = new HashMap<>();

  public void seed(LocalDate from, LocalDate to, Grain grain, Summary summary) {
    summaries.put(key(from, to, grain), summary);
  }

  @Override
  public Summary salesSummary(LocalDate from, LocalDate to, Grain grain) {
    return summaries.getOrDefault(key(from, to, grain), zeroSummary(from, to));
  }

  private static Summary zeroSummary(LocalDate from, LocalDate to) {
    long bucketCount = ChronoUnit.DAYS.between(from, to) + 1;
    List<Long> zeros = new ArrayList<>();
    for (long i = 0; i < bucketCount; i++) {
      zeros.add(0L);
    }
    return new Summary(0L, 0L, zeros, List.of());
  }

  private static String key(LocalDate from, LocalDate to, Grain grain) {
    return from + "|" + to + "|" + grain;
  }
}
