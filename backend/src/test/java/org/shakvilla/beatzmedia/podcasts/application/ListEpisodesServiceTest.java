package org.shakvilla.beatzmedia.podcasts.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.service.ListEpisodesService;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;
import org.shakvilla.beatzmedia.podcasts.fakes.FakeOwnershipReader;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/**
 * Unit tests for {@link ListEpisodesService} — episode list decorated with per-caller ownership
 * (LLFR-PODCAST-01.3). Proves: authenticated owner → {@code isOwned=true}; anonymous → always
 * {@code isOwned=false} with no ownership round-trip when there is nothing gated to check.
 */
@Tag("unit")
class ListEpisodesServiceTest {

  private static final AccountId OWNER = new AccountId("acct-owner");
  private static final AccountId NON_OWNER = new AccountId("acct-non-owner");
  private static final PodcastId SHOW = new PodcastId("show-1");
  private static final EpisodeId PREMIUM_EPISODE = new EpisodeId("ep-premium");
  private static final EpisodeId FREE_EPISODE = new EpisodeId("ep-free");
  private static final Instant PUBLISHED = Instant.parse("2026-06-01T00:00:00Z");

  FakePodcastRepository repository;
  FakeOwnershipReader ownership;
  ListEpisodesService service;

  @BeforeEach
  void setUp() {
    Podcast show =
        new Podcast(
            SHOW, "Show One", "Publisher", "img.png", PodcastCategory.CULTURE, null, 2, 10, null,
            true, PUBLISHED);
    repository =
        new FakePodcastRepository()
            .withShow(show)
            .withEpisode(
                new PodcastEpisode(
                    PREMIUM_EPISODE, SHOW, "Premium", "img.png", null, 1800, null, true,
                    Money.ofMinor(300, Currency.GHS), false, null, null, PUBLISHED, PUBLISHED))
            .withEpisode(
                new PodcastEpisode(
                    FREE_EPISODE, SHOW, "Free", "img.png", null, 1200, null, false, null, false,
                    null, null, PUBLISHED, PUBLISHED));
    ownership = new FakeOwnershipReader().markOwned(OWNER, PREMIUM_EPISODE);
    service = new ListEpisodesService(repository, ownership);
  }

  @Test
  void owner_seesIsOwnedTrue_onGatedEpisode() {
    List<PodcastEpisodeView> views = service.list(SHOW, Optional.of(OWNER));

    PodcastEpisodeView premium =
        views.stream().filter(v -> v.id().equals(PREMIUM_EPISODE.value())).findFirst().orElseThrow();
    assertTrue(premium.isOwned());
  }

  @Test
  void nonOwner_seesIsOwnedFalse_onGatedEpisode() {
    List<PodcastEpisodeView> views = service.list(SHOW, Optional.of(NON_OWNER));

    PodcastEpisodeView premium =
        views.stream().filter(v -> v.id().equals(PREMIUM_EPISODE.value())).findFirst().orElseThrow();
    assertFalse(premium.isOwned());
  }

  @Test
  void anonymousCaller_seesIsOwnedFalse_onGatedEpisode_noOwnershipRoundTrip() {
    List<PodcastEpisodeView> views = service.list(SHOW, Optional.empty());

    PodcastEpisodeView premium =
        views.stream().filter(v -> v.id().equals(PREMIUM_EPISODE.value())).findFirst().orElseThrow();
    assertFalse(premium.isOwned());
    assertEquals(0, ownership.ownedEpisodesCalls());
  }

  @Test
  void freeEpisode_isOwnedFieldAbsent_regardlessOfCaller() {
    List<PodcastEpisodeView> views = service.list(SHOW, Optional.of(OWNER));

    PodcastEpisodeView free =
        views.stream().filter(v -> v.id().equals(FREE_EPISODE.value())).findFirst().orElseThrow();
    assertEquals(null, free.isOwned());
  }

  @Test
  void unknownShow_throwsPodcastNotFound() {
    assertThrows(
        PodcastNotFoundException.class,
        () -> service.list(new PodcastId("does-not-exist"), Optional.empty()));
  }

  @Test
  void showTitle_isDenormalizedOntoEachEpisode() {
    List<PodcastEpisodeView> views = service.list(SHOW, Optional.empty());
    assertTrue(views.stream().allMatch(v -> "Show One".equals(v.showTitle())));
  }
}
