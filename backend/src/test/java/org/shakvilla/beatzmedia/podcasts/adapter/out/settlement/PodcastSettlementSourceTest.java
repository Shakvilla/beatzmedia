package org.shakvilla.beatzmedia.podcasts.adapter.out.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/** Unit tests for the WU-COM-4 podcast {@code SettlementSource} beans. */
class PodcastSettlementSourceTest {

  private static final Instant CREATED = Instant.parse("2026-07-01T00:00:00Z");

  private static Podcast show(String id, String creatorAccountId) {
    return new Podcast(
        new PodcastId(id), "Show One", "Pub", creatorAccountId, "img.png",
        PodcastCategory.CULTURE, "desc", 10, 90, null, true, CREATED);
  }

  private static PodcastEpisode episode(String id, String showId) {
    // premium=false so price may be null (settlement grants regardless of premium; it only reads ids).
    return new PodcastEpisode(
        new EpisodeId(id), new PodcastId(showId), "Ep", "ep.png", "d", 1800, 1, false, null, false,
        null, "asset", CREATED, CREATED);
  }

  // ---- episode ----

  @Test
  void episode_payeeIsShowCreator_grantsSingleEpisode() {
    var repo =
        new FakePodcastRepository()
            .withShow(show("show-1", "creator-1"))
            .withEpisode(episode("ep-1", "show-1"));
    var source = new EpisodeSettlementSource(repo);

    assertEquals(new AccountId("creator-1"), source.payee("ep-1").orElseThrow());
    assertEquals(List.of("ep-1"), source.ownedEpisodeIds("ep-1"));
    assertEquals("episode", source.entityType());
  }

  @Test
  void episode_showWithoutCreator_hasNoPayee() {
    var repo =
        new FakePodcastRepository()
            .withShow(show("show-1", null))
            .withEpisode(episode("ep-1", "show-1"));
    var source = new EpisodeSettlementSource(repo);

    assertFalse(source.payee("ep-1").isPresent());
  }

  @Test
  void episode_unknown_hasNoPayeeOrGrants() {
    var source = new EpisodeSettlementSource(new FakePodcastRepository());
    assertFalse(source.payee("nope").isPresent());
    assertTrue(source.ownedEpisodeIds("nope").isEmpty());
  }

  // ---- season pass ----

  @Test
  void seasonPass_payeeIsCreator_grantsAllShowEpisodes() {
    var repo =
        new FakePodcastRepository()
            .withShow(show("show-1", "creator-1"))
            .withEpisode(episode("ep-1", "show-1"))
            .withEpisode(episode("ep-2", "show-1"))
            .withEpisode(episode("ep-other", "show-2"));
    var source = new SeasonPassSettlementSource(repo);

    assertEquals(new AccountId("creator-1"), source.payee("show-1").orElseThrow());
    assertEquals(List.of("ep-1", "ep-2"), source.ownedEpisodeIds("show-1"));
    assertEquals("season-pass", source.entityType());
  }

  @Test
  void seasonPass_showWithoutCreator_hasNoPayee() {
    var repo = new FakePodcastRepository().withShow(show("show-1", null));
    var source = new SeasonPassSettlementSource(repo);
    assertFalse(source.payee("show-1").isPresent());
  }
}
