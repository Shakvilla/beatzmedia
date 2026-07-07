package org.shakvilla.beatzmedia.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RollupBucket} normalisation — required for the upsert-by-key invariant. */
@Tag("unit")
class RollupBucketTest {

  @Test
  void daily_bucketIsTheDayItself() {
    LocalDate date = LocalDate.parse("2026-07-05"); // a Sunday
    assertEquals(date, RollupBucket.startOf(date, Grain.DAILY));
  }

  @Test
  void weekly_bucketIsTheMondayOfTheIsoWeek() {
    LocalDate sunday = LocalDate.parse("2026-07-05");
    LocalDate monday = LocalDate.parse("2026-06-29");
    assertEquals(monday, RollupBucket.startOf(sunday, Grain.WEEKLY));
    // The Monday itself normalises to itself.
    assertEquals(monday, RollupBucket.startOf(monday, Grain.WEEKLY));
  }

  @Test
  void monthly_bucketIsTheFirstOfTheMonth() {
    LocalDate date = LocalDate.parse("2026-07-19");
    assertEquals(LocalDate.parse("2026-07-01"), RollupBucket.startOf(date, Grain.MONTHLY));
  }

  @Test
  void of_sameCalendarWeek_producesSameBucketRegardlessOfDayWithinIt() {
    RollupBucket monday = RollupBucket.of(LocalDate.parse("2026-06-29"), Grain.WEEKLY);
    RollupBucket sunday = RollupBucket.of(LocalDate.parse("2026-07-05"), Grain.WEEKLY);
    assertEquals(monday, sunday, "any day within the same ISO week normalises to the same bucket");
  }
}
