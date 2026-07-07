package org.shakvilla.beatzmedia.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupResult;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupWindow;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;
import org.shakvilla.beatzmedia.analytics.fakes.InMemorySaleFactRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemorySalesRollupRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryTipFactRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Unit tests for {@link SalesRollupJob} — proves the idempotent-upsert invariant (ADD §4.1): a
 * fact is folded into all three grains exactly once, and re-running the job with no new facts
 * leaves the rollup rows unchanged (no double-counting).
 */
@Tag("unit")
class SalesRollupJobTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-05T10:00:00Z");

  InMemorySaleFactRepository saleFacts;
  InMemoryTipFactRepository tipFacts;
  InMemorySalesRollupRepository rollups;
  SalesRollupJob job;

  @BeforeEach
  void setUp() {
    saleFacts = new InMemorySaleFactRepository();
    tipFacts = new InMemoryTipFactRepository();
    rollups = new InMemorySalesRollupRepository();
    job = new SalesRollupJob(saleFacts, tipFacts, rollups);
  }

  @Test
  void run_foldsUnprocessedSaleIntoAllThreeGrains() {
    saleFacts.append(SaleFact.unprocessed("f1", ARTIST.value(), 1000L, "GHS", OCCURRED_AT));

    RollupResult result = job.run(new RollupWindow(OCCURRED_AT));

    assertEquals(1, result.factsProcessed());
    assertEquals(3, result.bucketsUpserted(), "one bucket touched per grain (DAILY/WEEKLY/MONTHLY)");
    for (Grain grain : Grain.values()) {
      SalesRollup row =
          rollups
              .find(
                  ARTIST,
                  org.shakvilla.beatzmedia.analytics.domain.RollupBucket.startOf(
                      OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), grain),
                  grain)
              .orElseThrow();
      assertEquals(1000L, row.salesMinor(), "grain " + grain);
      assertEquals(1, row.units(), "grain " + grain);
      assertEquals(0L, row.royaltyMinor(), "OQ-4: royalty always 0");
    }
  }

  @Test
  void run_marksFactsProcessed_soARerunIsANoop_idempotent() {
    saleFacts.append(SaleFact.unprocessed("f1", ARTIST.value(), 1000L, "GHS", OCCURRED_AT));
    tipFacts.append(TipFact.unprocessed("t1", ARTIST.value(), 200L, "GHS", OCCURRED_AT));

    job.run(new RollupWindow(OCCURRED_AT));
    // Re-run: nothing left unprocessed, so this tick must be a pure no-op.
    RollupResult second = job.run(new RollupWindow(OCCURRED_AT.plusSeconds(300)));

    assertEquals(0, second.factsProcessed(), "no new facts on the re-run");
    assertEquals(0, second.bucketsUpserted());

    SalesRollup daily =
        rollups
            .find(ARTIST, OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), Grain.DAILY)
            .orElseThrow();
    assertEquals(1000L, daily.salesMinor(), "sale amount unchanged after re-run (no double count)");
    assertEquals(200L, daily.tipsMinor(), "tip amount unchanged after re-run (no double count)");
    assertEquals(1, daily.units());
  }

  @Test
  void run_multipleSalesSameArtistSameBucket_accumulate() {
    saleFacts.append(SaleFact.unprocessed("f1", ARTIST.value(), 500L, "GHS", OCCURRED_AT));
    saleFacts.append(SaleFact.unprocessed("f2", ARTIST.value(), 700L, "GHS", OCCURRED_AT));

    job.run(new RollupWindow(OCCURRED_AT));

    SalesRollup daily =
        rollups
            .find(ARTIST, OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), Grain.DAILY)
            .orElseThrow();
    assertEquals(1200L, daily.salesMinor());
    assertEquals(2, daily.units());
  }

  @Test
  void run_tipDoesNotIncrementUnits_onlySalesDo() {
    tipFacts.append(TipFact.unprocessed("t1", ARTIST.value(), 300L, "GHS", OCCURRED_AT));

    job.run(new RollupWindow(OCCURRED_AT));

    SalesRollup daily =
        rollups
            .find(ARTIST, OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), Grain.DAILY)
            .orElseThrow();
    assertEquals(300L, daily.tipsMinor());
    assertEquals(0, daily.units(), "a tip alone is not a unit sale");
    assertTrue(saleFacts.findUnprocessed().isEmpty());
  }
}
