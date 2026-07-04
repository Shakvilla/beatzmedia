package org.shakvilla.beatzmedia.podcasts.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the {@link EpisodeAccess} INV-3 gate — the truth table from the podcasts ADD §8
 * episode access state machine: free / premium-owned / premium-unowned / early-access
 * before/after {@code publicAt}, owned/unowned.
 */
@Tag("unit")
class EpisodeAccessTest {

  private static final Instant PUBLISHED = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant PUBLIC_AT = Instant.parse("2026-06-10T00:00:00Z");

  private static PodcastEpisode freeEpisode() {
    return new PodcastEpisode(
        new EpisodeId("ep-free"),
        new PodcastId("show-1"),
        "Free episode",
        "img.png",
        null,
        1200,
        null,
        false,
        null,
        false,
        null,
        null,
        PUBLISHED,
        PUBLISHED);
  }

  private static PodcastEpisode premiumEpisode() {
    return new PodcastEpisode(
        new EpisodeId("ep-premium"),
        new PodcastId("show-1"),
        "Premium episode",
        "img.png",
        null,
        1200,
        null,
        true,
        Money.ofMinor(300, Currency.GHS),
        false,
        null,
        null,
        PUBLISHED,
        PUBLISHED);
  }

  private static PodcastEpisode earlyAccessEpisode() {
    return new PodcastEpisode(
        new EpisodeId("ep-early"),
        new PodcastId("show-1"),
        "Early-access episode",
        "img.png",
        null,
        1200,
        null,
        false,
        Money.ofMinor(500, Currency.GHS),
        true,
        PUBLIC_AT,
        null,
        PUBLISHED,
        PUBLISHED);
  }

  // ---- Free: always accessible, regardless of ownership ----

  @Test
  void free_notOwned_isAccessible_noPreview() {
    EpisodeAccess access = EpisodeAccess.decide(freeEpisode(), false, false, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }

  @Test
  void free_owned_isAccessible_noPreview() {
    EpisodeAccess access = EpisodeAccess.decide(freeEpisode(), true, false, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }

  // ---- Premium: locked (preview only) until owned ----

  @Test
  void premium_notOwned_isLocked_previewOnly30s() {
    EpisodeAccess access = EpisodeAccess.decide(premiumEpisode(), false, false, 30);
    assertFalse(access.accessible());
    assertTrue(access.previewOnly());
    assertEquals(30, access.previewSec());
  }

  @Test
  void premium_owned_isAccessible_noPreview() {
    EpisodeAccess access = EpisodeAccess.decide(premiumEpisode(), true, false, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }

  // ---- Early-access: locked until owned OR now >= publicAt ----

  @Test
  void earlyAccess_beforePublicAt_notOwned_isLocked_previewOnly30s() {
    EpisodeAccess access = EpisodeAccess.decide(earlyAccessEpisode(), false, false, 30);
    assertFalse(access.accessible());
    assertTrue(access.previewOnly());
    assertEquals(30, access.previewSec());
  }

  @Test
  void earlyAccess_beforePublicAt_owned_isAccessible() {
    EpisodeAccess access = EpisodeAccess.decide(earlyAccessEpisode(), true, false, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }

  @Test
  void earlyAccess_atOrAfterPublicAt_notOwned_becomesFreeToEveryone() {
    EpisodeAccess access = EpisodeAccess.decide(earlyAccessEpisode(), false, true, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }

  @Test
  void earlyAccess_atOrAfterPublicAt_owned_isAccessible() {
    EpisodeAccess access = EpisodeAccess.decide(earlyAccessEpisode(), true, true, 30);
    assertTrue(access.accessible());
    assertFalse(access.previewOnly());
  }
}
