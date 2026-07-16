package org.shakvilla.beatzmedia.podcasts.adapter.out.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.fakes.FakePodcastRepository;

/** Unit tests for the WU-COM-4 podcast {@code ModulePriceSource} beans. */
class PodcastPriceSourceTest {

  private static final Instant CREATED = Instant.parse("2026-07-01T00:00:00Z");

  private static Podcast show(String id, Money seasonPassPrice) {
    return new Podcast(
        new PodcastId(id), "Show One", "Pub", "creator-1", "img.png",
        PodcastCategory.CULTURE, "desc", 10, 90, seasonPassPrice, true, CREATED);
  }

  private static PodcastEpisode episode(String id, String showId, boolean premium, Money price) {
    return new PodcastEpisode(
        new EpisodeId(id), new PodcastId(showId), "Ep One", "ep.png", "desc",
        1800, 1, premium, price, false, null, "asset-1", CREATED, CREATED);
  }

  // ---- episode ----

  @Test
  void episode_returnsAuthoritativePriceAndShowSubtitle() {
    var repo =
        new FakePodcastRepository()
            .withShow(show("show-1", null))
            .withEpisode(episode("ep-1", "show-1", true, Money.ofMinor(500, Currency.GHS)));
    var source = new EpisodePriceSource(repo);

    PricedItem priced = source.price("ep-1", Map.of());

    assertEquals("Ep One", priced.title());
    assertEquals("Show One", priced.subtitle());
    assertEquals("ep.png", priced.image());
    assertEquals(Money.ofMinor(500, Currency.GHS), priced.unitPrice());
    assertEquals("episode", source.entityType());
  }

  @Test
  void episode_freeEpisodeIsNotPurchasable() {
    var repo =
        new FakePodcastRepository()
            .withShow(show("show-1", null))
            .withEpisode(episode("ep-free", "show-1", false, null));
    var source = new EpisodePriceSource(repo);

    assertThrows(PriceUnavailableException.class, () -> source.price("ep-free", Map.of()));
  }

  @Test
  void episode_unknownIsNotFound() {
    var source = new EpisodePriceSource(new FakePodcastRepository());
    assertThrows(PriceUnavailableException.class, () -> source.price("nope", Map.of()));
  }

  // ---- season pass ----

  @Test
  void seasonPass_returnsShowPrice() {
    var repo = new FakePodcastRepository().withShow(show("show-1", Money.ofMinor(2000, Currency.GHS)));
    var source = new SeasonPassPriceSource(repo);

    PricedItem priced = source.price("show-1", Map.of());

    assertEquals("Show One", priced.title());
    assertEquals("Pub", priced.subtitle());
    assertEquals(Money.ofMinor(2000, Currency.GHS), priced.unitPrice());
    assertEquals("season-pass", source.entityType());
  }

  @Test
  void seasonPass_showWithoutPassIsNotPurchasable() {
    var repo = new FakePodcastRepository().withShow(show("show-1", null));
    var source = new SeasonPassPriceSource(repo);

    assertThrows(PriceUnavailableException.class, () -> source.price("show-1", Map.of()));
  }

  @Test
  void seasonPass_unknownShowIsNotFound() {
    var source = new SeasonPassPriceSource(new FakePodcastRepository());
    assertThrows(PriceUnavailableException.class, () -> source.price("nope", Map.of()));
  }
}
