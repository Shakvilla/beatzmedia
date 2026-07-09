package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;

/**
 * Unit tests for {@link Episode}'s state machine (INV-7) and the premium ⇒ price &gt; 0 guard.
 * LLFR-STUDIO-02.3 / 02.4.
 */
@Tag("unit")
class EpisodeTest {

  private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2026-07-01T00:00:00Z");

  private Episode draft() {
    return Episode.createDraft(
        new EpisodeId("ep-1"), new ShowId("sh-1"), new ArtistId("artist-1"), "Ep 1", "desc",
        "audio-key", null, 120, false, 0L, Currency.GHS, false, T0, "idem-1", "hash-1");
  }

  // ---- Premium ⇒ price > 0 ----

  @Test
  void createDraft_premiumWithZeroPrice_throwsInvalidPrice() {
    assertThrows(
        InvalidPriceException.class,
        () -> Episode.createDraft(
            new EpisodeId("ep-1"), new ShowId("sh-1"), new ArtistId("artist-1"), "Ep 1", "desc",
            "audio-key", null, 120, true, 0L, Currency.GHS, false, T0, "idem-1", "hash-1"));
  }

  @Test
  void createDraft_premiumWithPositivePrice_succeeds() {
    Episode e = Episode.createDraft(
        new EpisodeId("ep-1"), new ShowId("sh-1"), new ArtistId("artist-1"), "Ep 1", "desc",
        "audio-key", null, 120, true, 500L, Currency.GHS, false, T0, "idem-1", "hash-1");
    assertTrue(e.premium());
    assertEquals(500L, e.priceMinor());
  }

  @Test
  void updatePremiumPrice_toPremiumWithZeroPrice_throwsInvalidPrice() {
    Episode e = draft();
    assertThrows(InvalidPriceException.class, () -> e.updatePremiumPrice(true, 0L, Currency.GHS));
  }

  // ---- draft -> published (publish now) ----

  @Test
  void publishNow_fromDraft_transitionsAndSetsPublishedAt() {
    Episode e = draft();
    e.publishNow(FUTURE);
    assertEquals(EpisodeStatus.published, e.status());
    assertEquals(FUTURE, e.publishedAt());
    assertNull(e.scheduledAt());
  }

  @Test
  void publishNow_fromPublished_throwsIllegalTransition() {
    Episode e = draft();
    e.publishNow(FUTURE);
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.publishNow(FUTURE));
  }

  @Test
  void publishNow_fromScheduled_throwsIllegalTransition() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.publishNow(FUTURE));
  }

  // ---- draft -> scheduled ----

  @Test
  void scheduleAt_fromDraft_transitionsAndSetsScheduledAt() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    assertEquals(EpisodeStatus.scheduled, e.status());
    assertEquals(FUTURE, e.scheduledAt());
    assertNull(e.publishedAt());
  }

  @Test
  void scheduleAt_fromScheduled_throwsIllegalTransition() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.scheduleAt(FUTURE));
  }

  // ---- scheduled -> draft (unschedule) ----

  @Test
  void unschedule_fromScheduled_returnsToDraft() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    e.unschedule();
    assertEquals(EpisodeStatus.draft, e.status());
    assertNull(e.scheduledAt());
  }

  @Test
  void unschedule_fromDraft_throwsIllegalTransition() {
    Episode e = draft();
    assertThrows(IllegalEpisodeTransitionException.class, e::unschedule);
  }

  // ---- scheduled -> published (system-only, INV-7) ----

  @Test
  void goLive_fromScheduled_transitionsAndSetsPublishedAt() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    e.goLive(FUTURE);
    assertEquals(EpisodeStatus.published, e.status());
    assertEquals(FUTURE, e.publishedAt());
    assertNull(e.scheduledAt());
  }

  @Test
  void goLive_fromDraft_throwsIllegalTransition_noManualEarlyPublishViaGoLive() {
    Episode e = draft();
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.goLive(FUTURE));
  }

  @Test
  void goLive_fromPublished_throwsIllegalTransition_exactlyOnceGuard() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    e.goLive(FUTURE);
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.goLive(FUTURE));
  }

  // ---- reschedule ----

  @Test
  void reschedule_fromScheduled_updatesScheduledAt() {
    Episode e = draft();
    e.scheduleAt(FUTURE);
    Instant later = FUTURE.plusSeconds(3600);
    e.reschedule(later);
    assertEquals(later, e.scheduledAt());
    assertEquals(EpisodeStatus.scheduled, e.status());
  }

  @Test
  void reschedule_fromDraft_throwsIllegalTransition() {
    Episode e = draft();
    assertThrows(IllegalEpisodeTransitionException.class, () -> e.reschedule(FUTURE));
  }
}
