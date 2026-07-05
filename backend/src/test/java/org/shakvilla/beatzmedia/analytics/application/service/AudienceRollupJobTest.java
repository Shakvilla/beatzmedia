package org.shakvilla.beatzmedia.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupResult;
import org.shakvilla.beatzmedia.analytics.application.port.in.RollupWindow;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryAudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryFollowFactRepository;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryPlayFactRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Unit tests for {@link AudienceRollupJob} — mirrors {@link SalesRollupJobTest}'s idempotent-upsert
 * proof for plays/followers.
 */
@Tag("unit")
class AudienceRollupJobTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final Instant OCCURRED_AT = Instant.parse("2026-07-05T10:00:00Z");

  InMemoryPlayFactRepository playFacts;
  InMemoryFollowFactRepository followFacts;
  InMemoryAudienceRollupRepository rollups;
  AudienceRollupJob job;

  @BeforeEach
  void setUp() {
    playFacts = new InMemoryPlayFactRepository();
    followFacts = new InMemoryFollowFactRepository();
    rollups = new InMemoryAudienceRollupRepository();
    job = new AudienceRollupJob(playFacts, followFacts, rollups);
  }

  @Test
  void run_foldsPlaysAndFollowersIntoAllThreeGrains() {
    playFacts.append(PlayFact.unprocessed("p1", ARTIST.value(), "acc-1", OCCURRED_AT));
    playFacts.append(PlayFact.unprocessed("p2", ARTIST.value(), "acc-2", OCCURRED_AT));
    followFacts.append(FollowFact.unprocessed("fw1", ARTIST.value(), OCCURRED_AT));

    RollupResult result = job.run(new RollupWindow(OCCURRED_AT));

    assertEquals(3, result.factsProcessed());
    assertEquals(3, result.bucketsUpserted());
    for (Grain grain : Grain.values()) {
      AudienceRollup row =
          rollups
              .find(
                  ARTIST,
                  org.shakvilla.beatzmedia.analytics.domain.RollupBucket.startOf(
                      OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), grain),
                  grain)
              .orElseThrow();
      assertEquals(2L, row.plays(), "grain " + grain);
      assertEquals(1, row.followersGained(), "grain " + grain);
    }
  }

  @Test
  void run_reRunWithNoNewFacts_isANoop_idempotent() {
    playFacts.append(PlayFact.unprocessed("p1", ARTIST.value(), "acc-1", OCCURRED_AT));

    job.run(new RollupWindow(OCCURRED_AT));
    RollupResult second = job.run(new RollupWindow(OCCURRED_AT.plusSeconds(300)));

    assertEquals(0, second.factsProcessed());
    AudienceRollup daily =
        rollups
            .find(ARTIST, OCCURRED_AT.atZone(java.time.ZoneOffset.UTC).toLocalDate(), Grain.DAILY)
            .orElseThrow();
    assertEquals(1L, daily.plays(), "play count unchanged after re-run (no double count)");
  }
}
